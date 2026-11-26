// O contador de cota (regras em quota.js): um Durable Object por chave (aparelho ou rede), com SQLite.
import { DurableObject } from 'cloudflare:workers';

export class Quota extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS used (bucket TEXT NOT NULL, day INTEGER NOT NULL, n INTEGER NOT NULL, PRIMARY KEY (bucket, day))');
  }

  /** Soma 1 se ainda cabe. */
  async take(bucket, limit, day) {
    this.sql.exec('DELETE FROM used WHERE day < ?', day - 1);
    const row = this.sql.exec('SELECT n FROM used WHERE bucket = ? AND day = ?', bucket, day).toArray()[0];
    const n = row ? row.n : 0;
    if (n >= limit) return false;
    this.sql.exec('INSERT INTO used (bucket, day, n) VALUES (?, ?, 1) ON CONFLICT(bucket, day) DO UPDATE SET n = n + 1', bucket, day);
    return true;
  }
}
