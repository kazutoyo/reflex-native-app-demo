import { expect, test } from 'bun:test';

import { launch, TOKYO } from './sdk';

test('検索すると候補が出る', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: 'Tokyo' });
  const { state } = await app.do('Submit');
  expect(state.status).toBe('loaded');
  expect(state.results[0]).toEqual(TOKYO);
});

test('0 件でもエラーにならない', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: 'zzzzqqq' });
  expect((await app.do('Submit')).state).toMatchObject({ status: 'loaded', results: [] });
});

test('空のクエリでは検索しない', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: '  ' });
  expect((await app.do('Submit')).state.status).toBe('idle');
});

test('詳細でお気に入りにして戻ると、検索画面に反映される', async () => {
  const app = await launch({ route: '/search' });
  expect((await app.ls()).state.favorites).toEqual([]);

  expect(await app.do('Open', { id: TOKYO.id })).toMatchObject({
    path: '/cities/1850147',
    name: 'City',
    state: { status: 'loaded', city: { name: '東京都' }, favorite: false },
  });

  const toggled = await app.do('ToggleFavorite');
  expect(toggled.state.favorite).toBe(true);
  expect(toggled.env.favorites).toEqual([TOKYO.id]);

  expect(await app.do('Back')).toMatchObject({ path: '/search', state: { favorites: [{ id: TOKYO.id }] } });

  // Reopening creates a new screen (the old one was discarded on pop). Favorites are reloaded from storage
  expect((await app.do('Open', { id: TOKYO.id })).state).toMatchObject({ status: 'loaded', favorite: true });
});

test('知らない都市はフィクスチャがないのでエラー（ネットワークには出ない）', async () => {
  const app = await launch({ route: '/cities/999' });
  const { state } = await app.ls();
  expect(state.status).toBe('error');
  expect(state.message).toContain('フィクスチャがない');
});
