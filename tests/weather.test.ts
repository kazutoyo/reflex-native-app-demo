import { expect, test } from 'bun:test';

import { launch } from './sdk';

test('許可されていれば、起動するだけで現在地の天気が出る', async () => {
  const app = await launch();
  const { path, name, state } = await app.ls();
  expect({ path, name }).toEqual({ path: '/', name: 'Weather' });
  expect(state).toMatchObject({
    status: 'loaded',
    place: '新宿区',
    forecast: { current: { temperature: 23.5, code: 0 } },
  });
  expect(state.forecast.daily).toHaveLength(7);
});

test('拒否されたら、もう一度許可を求められる', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: true } });
  expect((await app.ls()).state).toEqual({ status: 'permissionDenied', canAskAgain: true });

  await app.env({ location: { granted: true } }); // The user granted permission
  expect((await app.do('Retry')).state.status).toBe('loaded');
});

test('二度と聞けないときは設定アプリを開く', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  expect((await app.ls()).state).toEqual({ status: 'permissionDenied', canAskAgain: false });
  expect((await app.do('OpenSettings')).env.settingsOpened).toBe(1);
});

test('拒否されたら都市検索へ逃がす', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  expect(await app.do('OpenSearch')).toMatchObject({ path: '/search', name: 'Search' });
});

test('位置が取れないときはエラー', async () => {
  const app = await launch({ location: { positionError: '位置を取得できない' } });
  expect((await app.ls()).state).toEqual({ status: 'error', message: '位置を取得できない' });
});

test('API が 500 ならエラー、直れば再試行で読める', async () => {
  const app = await launch({ http: { status: 500 } });
  expect((await app.ls()).state).toEqual({ status: 'error', message: 'HTTP 500' });

  await app.env({ http: 'fixtures' });
  expect((await app.do('Retry')).state.status).toBe('loaded');
});

test('設定で許可してから天気タブに戻ると、読み直す', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  await app.do('OpenSettings');
  await app.do('OpenSearch');

  await app.env({ location: { granted: true } }); // Granted in the Settings app
  expect(await app.do('SelectTab', { tab: 'weather' })).toMatchObject({
    name: 'Weather',
    state: { status: 'loaded' },
  });
});

test('読み込めたあとはタブを行き来しても取り直さない', async () => {
  const app = await launch();
  await app.do('OpenSearch');
  const { env } = await app.do('SelectTab', { tab: 'weather' });
  expect(env.httpRequests).toBe(1);
});

test('地名が取れなくても天気は出す', async () => {
  const app = await launch({ location: { placeError: '逆ジオコーディング失敗' } });
  expect((await app.ls()).state).toMatchObject({ status: 'loaded', place: null });
});
