"""Coleta para a frente (24/11/2026): a previsão de hoje de cada fonte para os próximos 4 dias, em 30 aeroportos.
Depois, analise_coleta.py confere com o METAR (o que a estação viu). Não faz parte do build.
Fontes: MET Norway, WeatherAPI, OpenWeather e Visual Crossing (essas três só se a chave existir no ambiente),
Open-Meteo (best_match = o que o plano pago entrega) e os 4 modelos abertos (para o "consenso" grátis).
Saída: acrescenta linhas em ferramentas/clima/coleta.csv."""
import csv, json, os, sys, time, urllib.parse, urllib.request
from collections import defaultdict
from datetime import datetime, timedelta, timezone

AQUI = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(AQUI, "coleta.csv")
UA = "Sofaly/1.0 (+https://sofaly.tv) weather-source-test"
ABERTOS = ["ecmwf_ifs", "ecmwf_aifs025_single", "gfs_seamless", "icon_global"]
ESTACOES = {
    "SBSP": (-23.627, -46.655), "SBRJ": (-22.910, -43.163), "SBCF": (-19.634, -43.969), "SBEG": (-3.039, -60.050),
    "SBPA": (-29.994, -51.171), "SBBR": (-15.863, -47.913), "SBSV": (-12.911, -38.331), "SBRF": (-8.126, -34.924),
    "SBFZ": (-3.776, -38.533), "SBCT": (-25.528, -49.176), "SBFL": (-27.670, -48.547), "SBGO": (-16.632, -49.221),
    "SBBE": (-1.379, -48.476), "SBCY": (-15.653, -56.117), "SBVT": (-20.258, -40.286), "SBNT": (-5.911, -35.248),
    "SBSL": (-2.585, -44.234), "SBTE": (-5.060, -42.823), "SBLO": (-23.334, -51.130), "SBUL": (-18.883, -48.220),
    "EGLL": (51.478, -0.461), "LFPG": (49.013, 2.550), "LEMD": (40.494, -3.567), "EDDB": (52.380, 13.523),
    "KJFK": (40.639, -73.762), "KMIA": (25.791, -80.316), "MMMX": (19.436, -99.072), "SAEZ": (-34.822, -58.536),
    "RJTT": (35.552, 139.780), "YSSY": (-33.946, 151.177),
}

def get(url):
    for t in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
            return json.loads(urllib.request.urlopen(req, timeout=60).read().decode())
        except Exception as e:
            erro = e; time.sleep(3 * (t + 1))
    # nunca imprime a URL (pode ter chave)
    print("  falhou:", type(erro).__name__, getattr(erro, "code", ""), file=sys.stderr)
    return None

def linhas_open_meteo(lat, lon):
    """(offset, {fonte: {dia: (mm, chance, tmax, tmin)}})"""
    q = dict(latitude=lat, longitude=lon, timezone="auto", forecast_days=5,
             daily="precipitation_sum,precipitation_probability_max,temperature_2m_max,temperature_2m_min",
             models=",".join(["best_match"] + ABERTOS))
    j = get("https://api.open-meteo.com/v1/forecast?" + urllib.parse.urlencode(q))
    if not j: return None, {}
    d = j["daily"]; out = {}
    for m in ["best_match"] + ABERTOS:
        out["openmeteo_" + m] = {dia: (d.get(f"precipitation_sum_{m}", [None] * 9)[i], d.get(f"precipitation_probability_max_{m}", [None] * 9)[i],
                                       d.get(f"temperature_2m_max_{m}", [None] * 9)[i], d.get(f"temperature_2m_min_{m}", [None] * 9)[i])
                                 for i, dia in enumerate(d["time"])}
    return j["utc_offset_seconds"], out

def linhas_met(lat, lon, off):
    j = get(f"https://api.met.no/weatherapi/locationforecast/2.0/complete?lat={lat:.4f}&lon={lon:.4f}")
    if not j: return {}
    mm = defaultdict(float); temps = defaultdict(list); coberto = defaultdict(set)
    for p in j["properties"]["timeseries"]:
        t = datetime.fromisoformat(p["time"].replace("Z", "+00:00")) + timedelta(seconds=off)
        dia = t.strftime("%Y-%m-%d")
        temps[dia].append(p["data"]["instant"]["details"]["air_temperature"])
        for chave, horas in (("next_1_hours", 1), ("next_6_hours", 6)):
            bloco = p["data"].get(chave)
            if not bloco: continue
            ini = t.hour
            if any(h in coberto[dia] for h in range(ini, ini + horas)): break
            mm[dia] += bloco["details"].get("precipitation_amount", 0.0)
            coberto[dia].update(range(ini, ini + horas))
            break
    return {d: (round(mm[d], 2), None, max(temps[d]), min(temps[d])) for d in temps}

def linhas_weatherapi(lat, lon):
    k = os.environ.get("WEATHERAPI_CHAVE")
    if not k: return {}
    j = get("https://api.weatherapi.com/v1/forecast.json?" + urllib.parse.urlencode(dict(key=k, q=f"{lat},{lon}", days=5, aqi="no", alerts="no")))
    if not j: return {}
    return {f["date"]: (f["day"]["totalprecip_mm"], f["day"].get("daily_chance_of_rain"), f["day"]["maxtemp_c"], f["day"]["mintemp_c"])
            for f in j["forecast"]["forecastday"]}

def linhas_owm(lat, lon, off):
    k = os.environ.get("OWM_CHAVE")
    if not k: return {}
    j = get("https://api.openweathermap.org/data/3.0/onecall?" + urllib.parse.urlencode(dict(lat=lat, lon=lon, exclude="current,minutely,alerts", units="metric", appid=k)))
    if not j: return {}
    out = {}
    for d in j.get("daily", []):
        dia = (datetime.fromtimestamp(d["dt"], timezone.utc) + timedelta(seconds=off)).strftime("%Y-%m-%d")
        out[dia] = (d.get("rain", 0.0) + d.get("snow", 0.0), round(d.get("pop", 0) * 100), d["temp"]["max"], d["temp"]["min"])
    return out

def linhas_vc(lat, lon):
    k = os.environ.get("VISUALCROSSING_CHAVE")
    if not k: return {}
    j = get(f"https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/{lat},{lon}/next4days?" +
            urllib.parse.urlencode(dict(unitGroup="metric", include="days", elements="datetime,precip,precipprob,tempmax,tempmin", key=k)))
    if not j: return {}
    return {d["datetime"]: (d.get("precip"), d.get("precipprob"), d.get("tempmax"), d.get("tempmin")) for d in j.get("days", [])}

def main():
    agora = datetime.now(timezone.utc)
    novas = []
    for est, (lat, lon) in ESTACOES.items():
        off, fontes = linhas_open_meteo(lat, lon)
        if off is None: continue
        hoje = (agora + timedelta(seconds=off)).date()
        fontes["met_norway"] = linhas_met(lat, lon, off)
        fontes["weatherapi"] = linhas_weatherapi(lat, lon)
        fontes["openweather"] = linhas_owm(lat, lon, off)
        fontes["visualcrossing"] = linhas_vc(lat, lon)
        for fonte, dias in fontes.items():
            for dia, (mm, chance, tmax, tmin) in dias.items():
                lead = (datetime.strptime(dia, "%Y-%m-%d").date() - hoje).days
                if 0 <= lead <= 4:
                    novas.append(dict(coletado=agora.strftime("%Y-%m-%dT%H:%MZ"), est=est, fonte=fonte, dia=dia, lead=lead,
                                      mm=mm, chance=chance, tmax=tmax, tmin=tmin))
        time.sleep(2)
    existe = os.path.exists(SAIDA)
    with open(SAIDA, "a", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(novas[0].keys()))
        if not existe: w.writeheader()
        w.writerows(novas)
    por = defaultdict(int)
    for n in novas: por[n["fonte"]] += 1
    print(agora.isoformat(), dict(por))

main()
