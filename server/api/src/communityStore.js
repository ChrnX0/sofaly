// O Durable Object das fichas comunitárias (regras e rotas em community.js). Um só, com SQLite (plano grátis).
import { DurableObject } from 'cloudflare:workers';
<<<<<<< HEAD
import { DAY_MS, MAX_PUBLISHED, MIN_HOMES, WINDOW_DAYS, parseKey } from './community.js';
=======
import { DAY_MS, MAX_PUBLISHED, MAX_ROWS, MIN_HOMES, WINDOW_DAYS, parseKey } from './community.js';
>>>>>>> origin/main

export class Community extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.sql.exec('CREATE TABLE IF NOT EXISTS reports (lesson TEXT NOT NULL, home TEXT NOT NULL, day INTEGER NOT NULL, PRIMARY KEY (lesson, home))');
  }

  async report(home, lessons, now = Date.now()) {
    const day = Math.floor(now / DAY_MS);
<<<<<<< HEAD
=======
    this.sql.exec('DELETE FROM reports WHERE day < ?', day - WINDOW_DAYS);
    // Teto de linhas (revisor, 25/11/2026): a janela de 14 dias sozinha não limita quem manda muito de muitos IPs.
    const n = this.sql.exec('SELECT COUNT(*) AS n FROM reports').one().n;
    if (n + lessons.length > MAX_ROWS) return { ok: false, error: 'full' };
>>>>>>> origin/main
    for (const l of lessons) {
      this.sql.exec('INSERT INTO reports (lesson, home, day) VALUES (?, ?, ?) ON CONFLICT(lesson, home) DO UPDATE SET day = excluded.day', l, home, day);
    }
    this.sql.exec('DELETE FROM reports WHERE day < ?', day - WINDOW_DAYS);
    return { ok: true };
  }

  async list(now = Date.now()) {
    const day = Math.floor(now / DAY_MS);
    const rows = this.sql.exec('SELECT lesson, COUNT(*) AS homes FROM reports WHERE day >= ? GROUP BY lesson HAVING homes >= ? ORDER BY homes DESC LIMIT ?',
      day - WINDOW_DAYS, MIN_HOMES, MAX_PUBLISHED).toArray();
    return rows.map((r) => ({ ...parseKey(r.lesson), homes: r.homes })).filter((r) => r.pkg);
  }
}

