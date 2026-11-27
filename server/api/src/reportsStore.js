// A fila particular de relatos (regras em reports.js): um Durable Object só, com SQLite.
import { DurableObject } from 'cloudflare:workers';
import { KEEP_DAYS, MAX_ROWS } from './reports.js';

export class Reports extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS reports (n INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, device TEXT NOT NULL, data TEXT NOT NULL)');
  }

  limpar(now) { this.sql.exec('DELETE FROM reports WHERE at < ?', now - KEEP_DAYS * 86_400_000); }

  async add(device, report, now) {
    this.limpar(now);
    if (this.sql.exec('SELECT COUNT(*) AS c FROM reports').one().c >= MAX_ROWS) return { ok: false, error: 'full' };
    this.sql.exec('INSERT INTO reports (at, device, data) VALUES (?, ?, ?)', now, device, JSON.stringify(report));
    return { ok: true, n: this.sql.exec('SELECT last_insert_rowid() AS n').one().n };
  }

  async list(after, now) {
    this.limpar(now);
    return this.sql.exec('SELECT n, at, device, data FROM reports WHERE n > ? ORDER BY n LIMIT 100', after).toArray()
      .map((r) => ({ n: r.n, at: r.at, device: r.device, ...JSON.parse(r.data) }));
  }
}
