#!/usr/bin/env bun
// CLI for humans and agents. Like Reflex, it prints no colors or spinners.
//
//   bun cli.ts [--platform ios|android | --connect host:port] [--launch '<json>'] <command>...
//     ls                     State and actions of the current screen
//     do <Action> [json]     Send an action          e.g. do Open '{"id":1850147}'
//     env <json>             Change the device environment  e.g. env '{"http":"offline"}' / env '{"weatherCode":61}'
//     open <route>           Deep link (app only)
//
//   bun cli.ts --platform android --launch '{"route":"/search"}' do ChangeQuery '{"query":"Tokyo"}' do Submit
//   bun cli.ts --connect localhost:8765 ls do SelectTab '{"tab":"favorites"}'
import { performance } from 'node:perf_hooks';

import { Session, type Platform } from './sdk';

const args = process.argv.slice(2);
let platform: Platform = 'ios';
let address: string | undefined;
let launch: object = {};
const cmds: object[] = [];
const json = (s: string | undefined) => (s?.startsWith('{') ? JSON.parse(args.shift()!) : {});

while (args.length) {
  const a = args.shift()!;
  if (a === '--platform') platform = args.shift() as Platform;
  else if (a === '--connect') address = args.shift();
  else if (a === '--launch') launch = JSON.parse(args.shift()!);
  else if (a === 'ls') cmds.push({ cmd: 'ls' });
  else if (a === 'do') cmds.push({ cmd: 'do', action: args.shift(), input: json(args[0]) });
  else if (a === 'env') cmds.push({ cmd: 'env', ...json(args[0]) });
  else if (a === 'open') cmds.push({ cmd: 'open', route: args.shift() });
  else {
    console.error(`不明な引数: ${a}`);
    process.exit(2);
  }
}
if (!cmds.length) cmds.push({ cmd: 'ls' });

const session = address ? await Session.connect(address) : await Session.spawn(platform);
if (!address) cmds.unshift({ cmd: 'launch', ...launch });

let failed = false;
for (const c of cmds as any[]) {
  console.log(`$ ${[c.cmd, c.action ?? c.route, c.input && Object.keys(c.input).length ? JSON.stringify(c.input) : ''].filter(Boolean).join(' ')}`);
  const t = performance.now();
  try {
    const r = await session.request(c);
    console.log(`  (${(performance.now() - t).toFixed(1)} ms)`);
    console.log(`  ${r.path}  ${r.name}`);
    console.log(`  state:   ${JSON.stringify(r.state)}`);
    console.log(`  actions: ${r.actions.join(', ')}`);
  } catch (e) {
    console.log(`  error: ${(e as Error).message}`);
    failed = true;
  }
}
session.close();
process.exit(failed ? 1 : 0);
