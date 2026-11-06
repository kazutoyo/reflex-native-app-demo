// SDK for behavior tests. Sends the same JSON lines (NDJSON) to the iOS (Swift) and Android (Kotlin) CLIs.
// PLATFORM (ios / android) selects the target. Test files do not know the platform.
import { spawn, type Subprocess } from 'bun';
import path from 'node:path';

export type Platform = 'ios' | 'android' | 'rn';
export const PLATFORM = (process.env.PLATFORM ?? 'ios') as Platform;

const ROOT = path.resolve(import.meta.dir, '..');
export const FIXTURES = path.join(ROOT, 'fixtures');
const COMMANDS: Record<Exclude<Platform, 'rn'>, string[]> = {
  ios: [path.join(ROOT, 'ios/.build/debug/weather-cli')],
  android: [path.join(ROOT, 'android/cli/build/install/cli/bin/cli')],
};
/** The React Native version (reflex-expo-app). Speaks the same protocol over TCP inside a jest harness */
const RN_APP = process.env.RN_APP ?? path.resolve(ROOT, '../reflex-expo-app');
const RN_PORT = 8770;

/** Device environment. Pass initial values with launch; change them later with env (e.g. the user granted permission in Settings). */
export type Environment = {
  location?: {
    granted?: boolean;
    canAskAgain?: boolean;
    /** Getting the current location fails with this message */
    positionError?: string;
    /** Reverse geocoding result. null means "unknown" */
    place?: string | null;
    placeError?: string;
  };
  /** fixtures returns saved responses. offline cannot connect. status always returns that status */
  http?: 'fixtures' | 'offline' | { status: number };
  /** Overrides today's weather with this WMO code (for rain, snow, and similar cases) */
  weatherCode?: number;
};

export type LaunchOptions = Environment & {
  /** Deep link. Omit to launch as if from the app icon */
  route?: string;
  /** Saved favorites (city IDs) */
  favorites?: number[];
};

export type Node = {
  path: string;
  name: string;
  state: any;
  /** Actions of the current screen plus those lent by its parents (tabs, stack) */
  actions: string[];
  /** For tests: what is observable from outside the app */
  env: { settingsOpened: number; favorites: number[]; httpRequests: number; networkViolations: number };
};

/** Sends one line of JSON and receives one line of JSON. The peer is a CLI process or the in-app server. */
export class Session {
  private constructor(
    private write: (line: string) => void,
    private lines: AsyncIterator<string>,
    public close: () => void,
    private label: string
  ) {}

  /** Headless: runs Core in a CLI process */
  static async spawn(platform: Platform): Promise<Session> {
    if (platform === 'rn') return Session.spawnRN();
    const proc: Subprocess<'pipe', 'pipe', 'inherit'> = spawn(COMMANDS[platform], {
      stdin: 'pipe',
      stdout: 'pipe',
      stderr: 'inherit',
      env: { ...process.env, FIXTURES },
    });
    const write = (line: string) => {
      proc.stdin.write(line);
      proc.stdin.flush();
    };
    return new Session(write, readLines(proc.stdout), () => proc.kill(), `${platform} の CLI`);
  }

  private static async spawnRN() {
    const proc = spawn(['npx', 'jest', '--no-watchman', '--silent', 'test/protocol.test.tsx'], {
      cwd: RN_APP,
      env: { ...process.env, REFLEX_PORT: String(RN_PORT) },
      stdout: 'ignore',
      stderr: 'ignore',
    });
    for (let i = 0; i < 300; i++) {
      try {
        const session = await Session.connect(`127.0.0.1:${RN_PORT}`);
        const close = session.close;
        return Object.assign(session, { close: () => (close(), proc.kill()) });
      } catch {
        await Bun.sleep(100);
      }
    }
    proc.kill();
    throw new Error(`RN のハーネスにつながらない（${RN_APP}）`);
  }

  /** Connects to the in-app server of a development build (iOS Simulator via localhost, Android via adb forward) */
  static async connect(address: string) {
    const [hostname, port] = address.split(':');
    const stream = new TransformStream<Uint8Array>();
    const writer = stream.writable.getWriter();
    const socket = await Bun.connect({
      hostname,
      port: Number(port),
      socket: { data: (_s, data) => void writer.write(data) },
    });
    return new Session((line) => socket.write(line), readLines(stream.readable), () => socket.end(), address);
  }

  async request(body: object): Promise<any> {
    this.write(JSON.stringify(body) + '\n');
    const { value, done } = await this.lines.next();
    if (done) throw new Error(`${this.label} が終了した`);
    const res = JSON.parse(value);
    if (!res.ok) throw new Error(res.error);
    if (res.env?.networkViolations) throw new Error(`ネットワークに出ようとした（${res.env.networkViolations} 回）`);
    return res;
  }
}

async function* readLines(stream: ReadableStream<Uint8Array>) {
  const decoder = new TextDecoder();
  let buffer = '';
  for await (const chunk of stream) {
    buffer += decoder.decode(chunk, { stream: true });
    let i;
    while ((i = buffer.indexOf('\n')) >= 0) {
      yield buffer.slice(0, i);
      buffer = buffer.slice(i + 1);
    }
  }
}

// CLI processes are slow to start (especially the JVM), so reuse them and recreate the app with launch
let session: Promise<Session> | undefined;
process.on('exit', () => void session?.then((s) => s.close()));

export async function launch(options: LaunchOptions = {}) {
  session ??= Session.spawn(PLATFORM);
  const s = await session;
  await s.request({ cmd: 'launch', ...options });
  return {
    /** name / state / actions of the current screen. */
    ls: (): Promise<Node> => s.request({ cmd: 'ls' }),
    /** Sends an action. Returns a fresh ls after all side effects (API calls, etc.) finish. */
    do: (action: string, input: object = {}): Promise<Node> => s.request({ cmd: 'do', action, input }),
    /** Changes the device environment. */
    env: (env: Environment): Promise<Node> => s.request({ cmd: 'env', ...env }),
  };
}

export const TOKYO = { id: 1850147, name: '東京都', region: '東京都, 日本', latitude: 35.6895, longitude: 139.69171 };
