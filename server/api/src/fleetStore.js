// O banco da rede de Sofys (regras em fleet.js): um Durable Object só, com SQLite.
// Uma linha por caso; o mesmo código no mesmo dia substitui o pacote anterior (a TV pode mandar de novo sem duplicar).
import { DurableObject } from 'cloudflare:workers';
import { DAY_MS, KEEP_DAYS, MAX_ROWS, minHomes, summarize } from './fleet.js';

export class Fleet extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS cases (day INTEGER NOT NULL, code TEXT NOT NULL, app INTEGER NOT NULL, device TEXT NOT NULL, data TEXT NOT NULL)');
    this.sql.exec('CREATE INDEX IF NOT EXISTS cases_day ON cases (day, code)');
    // Quem mandou o pacote do dia, mesmo sem caso: é o tamanho da frota que decide o limiar de casas.
    this.sql.exec('CREATE TABLE IF NOT EXISTS homes (day INTEGER NOT NULL, code TEXT NOT NULL, PRIMARY KEY (day, code))');
  }

  clean(day) {
    this.sql.exec('DELETE FROM cases WHERE day < ?', day - KEEP_DAYS);
    this.sql.exec('DELETE FROM homes WHERE day < ?', day - KEEP_DAYS);
  }

  async add(p, now) {
    const day = Math.floor(now / DAY_MS);
    this.clean(day);
    this.sql.exec('DELETE FROM cases WHERE day = ? AND code = ?', day, p.code);
    this.sql.exec('INSERT OR IGNORE INTO homes (day, code) VALUES (?, ?)', day, p.code);
    const n = this.sql.exec('SELECT COUNT(*) AS n FROM cases').one().n;
    if (n + p.cases.length > MAX_ROWS) return { ok: false, error: 'full' };
    const device = JSON.stringify(p.device);
    for (const c of p.cases) {
      this.sql.exec('INSERT INTO cases (day, code, app, device, data) VALUES (?, ?, ?, ?, ?)', day, p.code, p.app, device, JSON.stringify(c));
    }
    return { ok: true, cases: p.cases.length };
  }

  async summary(now, forced) {
    const day = Math.floor(now / DAY_MS);
    this.clean(day);
    // Casas ativas = o maior número de códigos num dia, nos últimos 7 dias (também conta quem mandou 0 casos).
    const active = this.sql.exec('SELECT MAX(n) AS n FROM (SELECT COUNT(*) AS n FROM homes WHERE day >= ? GROUP BY day)', day - 7).one().n ?? 0;
    const k = minHomes(active, forced);
    const rows = this.sql.exec('SELECT day, code, app, device, data FROM cases WHERE day >= ?', day - KEEP_DAYS).toArray()
      .map((r) => ({ day: r.day, code: r.code, app: r.app, device: JSON.parse(r.device), c: JSON.parse(r.data) }));
    return { min_homes: k, active_homes: active, keep_days: KEEP_DAYS, groups: summarize(rows, k) };
  }
}
