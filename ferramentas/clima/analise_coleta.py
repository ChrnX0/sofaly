"""Placar da coleta para a frente (24/11/2026): confere cada previsão de coleta.csv com o que a estação do aeroporto
viu (METAR, arquivo da Iowa State). Não faz parte do build. Uso: python3 -I analise_coleta.py [pasta_cache]

Conta só dias locais que já acabaram e com relato METAR em pelo menos 18 das 24 horas.
- Brier: (chance/100 - choveu)^2, média; menor é melhor. Fonte sem chance (MET Norway) usa 1 se mm >= 1, senão 0.
- Acerto "chove?": previu chuva (chance >= 50, ou mm >= 1 sem chance) igual ao que aconteceu.
- Erro de temperatura: |máx prevista - máx vista| e o mesmo para a mínima, média em °C.
"Choveu" = alguma hora do dia com chuva, garoa, granizo ou neve no METAR (sem os "VC", que são nas vizinhanças)."""
import csv, os, re, sys, time, urllib.parse, urllib.request
from collections import defaultdict
from datetime import datetime, timedelta, timezone

AQUI = os.path.dirname(os.path.abspath(__file__))
CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, ".cache")
CHUVA = re.compile(r"(RA|DZ|GR|GS|SN|PL|UP)")
# Fuso fixo de cada estação (sem horário de verão no período do teste, nov-dez; Europa/EUA já em horário padrão).
FUSO = {"SBSP": -3, "SBRJ": -3, "SBCF": -3, "SBEG": -4, "SBPA": -3, "SBBR": -3, "SBSV": -3, "SBRF": -3, "SBFZ": -3,
        "SBCT": -3, "SBFL": -3, "SBGO": -3, "SBBE": -3, "SBCY": -4, "SBVT": -3, "SBNT": -3, "SBSL": -3, "SBTE": -3,
        "SBLO": -3, "SBUL": -3, "EGLL": 0, "LFPG": 1, "LEMD": 1, "EDDB": 1, "KJFK": -5, "KMIA": -5, "MMMX": -6,
        "SAEZ": -3, "RJTT": 9, "YSSY": 11}
NOMES = {"sofaly": "Sofaly (no ar)", "openmeteo_best_match": "Open-Meteo padrão", "met_norway": "MET Norway",
         "weatherapi": "WeatherAPI", "openweather": "OpenWeather", "visualcrossing": "Visual Crossing"}

def metar(est, d1, d2):
    os.makedirs(CACHE, exist_ok=True)
    p = os.path.join(CACHE, f"metar_{est}_{d1}_{d2}.csv")
    if not os.path.exists(p):
        a, b = datetime.fromisoformat(d1) - timedelta(days=1), datetime.fromisoformat(d2) + timedelta(days=1)
        q = dict(station=est, data=["tmpc", "wxcodes"], year1=a.year, month1=a.month, day1=a.day, year2=b.year,
                 month2=b.month, day2=b.day, tz="Etc/UTC", format="onlycomma", latlon="no", missing="M", direct="no",
                 report_type=["3", "4"])
        url = "https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py?" + urllib.parse.urlencode(q, doseq=True)
        for t in range(4):
            try:
                open(p, "w").write(urllib.request.urlopen(url, timeout=120).read().decode()); break
            except Exception as e:
                print("  METAR falhou", est, type(e).__name__, file=sys.stderr); time.sleep(5 * (t + 1))
        else:
            return {}
    horas = defaultdict(lambda: [[], False])
    for r in csv.DictReader(open(p).read().splitlines()):
        t = datetime.strptime(r["valid"], "%Y-%m-%d %H:%M") + timedelta(hours=FUSO[est])
        h = horas[t.strftime("%Y-%m-%dT%H")]
        if r["tmpc"] not in ("M", ""): h[0].append(float(r["tmpc"]))
        wx = " ".join(w for w in (r["wxcodes"] or "").split() if not w.startswith("VC"))
        if wx != "M" and CHUVA.search(wx): h[1] = True
    dias = defaultdict(list)
    for k, (temps, chuva) in horas.items(): dias[k[:10]].append((temps, chuva))
    out = {}
    for dia, hs in dias.items():
        temps = [x for ts, _ in hs for x in ts]
        if len(hs) >= 18 and temps:
            out[dia] = dict(choveu=any(c for _, c in hs), tmax=max(temps), tmin=min(temps))
    return out

def num(x):
    try: return float(x)
    except (TypeError, ValueError): return None

def main():
    linhas, vistas = [], set()
    for r in csv.DictReader(open(os.path.join(AQUI, "coleta.csv"))):
        chave = (r["est"], r["fonte"], r["dia"], r["lead"])
        if chave in vistas: continue   # se a coleta rodou duas vezes no dia, vale a primeira
        vistas.add(chave); linhas.append(r)
    agora = datetime.now(timezone.utc)
    acabou = lambda est, dia: (agora + timedelta(hours=FUSO[est])).strftime("%Y-%m-%d") > dia
    # Prazo 0 fica de fora: a coleta roda de manhã (UTC) e parte do dia já passou (a máxima de algumas fontes só cobre o resto).
    linhas = [r for r in linhas if int(r["lead"]) >= 1 and acabou(r["est"], r["dia"])]
    if not linhas:
        print("Nenhum dia previsto acabou ainda."); return
    d1, d2 = min(r["dia"] for r in linhas), max(r["dia"] for r in linhas)
    obs = {est: metar(est, d1, d2) for est in sorted({r["est"] for r in linhas})}
    s = defaultdict(lambda: defaultdict(list))   # (fonte, lead) -> métrica -> valores
    for r in linhas:
        o = obs.get(r["est"], {}).get(r["dia"])
        if not o: continue
        mm, ch, tx, tn = num(r["mm"]), num(r["chance"]), num(r["tmax"]), num(r["tmin"])
        p = ch / 100 if ch is not None else (1.0 if mm is not None and mm >= 1 else 0.0 if mm is not None else None)
        g = s[(r["fonte"], int(r["lead"]))]
        if p is not None:
            g["brier"].append((p - o["choveu"]) ** 2)
            g["acerto"].append((p >= 0.5) == o["choveu"])
        if tx is not None: g["tmax"].append(abs(tx - o["tmax"]))
        if tn is not None: g["tmin"].append(abs(tn - o["tmin"]))
    m = lambda v: sum(v) / len(v) if v else None
    fontes = [f for f in NOMES if any(k[0] == f for k in s)]
    print(f"Dias conferidos: {d1} a {d2}, {len(obs)} aeroportos.\n")
    for lead in sorted({k[1] for k in s}):
        print(f"Previsão feita {lead} dia(s) antes")
        print(f"  {'fonte':22} {'n':>4} {'Brier':>6} {'acerto':>7} {'erro máx':>9} {'erro mín':>9}")
        ordem = lambda f: m(s[(f, lead)]["brier"]) if s[(f, lead)]["brier"] else 9
        for f in sorted(fontes, key=ordem):
            g = s.get((f, lead))
            if not g or not g["brier"]: continue
            print(f"  {NOMES[f]:22} {len(g['brier']):>4} {m(g['brier']):6.3f} {100 * m(g['acerto']):6.0f}% "
                  f"{m(g['tmax']) or 0:8.1f}° {m(g['tmin']) or 0:8.1f}°")
        print()

main()
