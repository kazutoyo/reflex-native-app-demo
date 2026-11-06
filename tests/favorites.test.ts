import { expect, test } from 'bun:test';

import { launch, TOKYO } from './sdk';

test('お気に入りがなければ空', async () => {
  const app = await launch({ route: '/favorites' });
  expect(await app.ls()).toMatchObject({ name: 'Favorites', state: { status: 'loaded', items: [] } });
});

test('お気に入りの都市と今の天気が並ぶ', async () => {
  const app = await launch({ route: '/favorites', favorites: [TOKYO.id] });
  expect((await app.ls()).state.items).toEqual([
    { city: TOKYO, current: { temperature: 23.5, code: 0 }, failed: false },
  ]);
});

test('天気が取れない都市も一覧には残る', async () => {
  const app = await launch({ route: '/favorites', favorites: [TOKYO.id], http: { status: 503 } });
  expect((await app.ls()).state.items).toEqual([{ city: TOKYO, current: null, failed: true }]);
});

test('開いてお気に入りを外し、戻ると一覧から消える', async () => {
  const app = await launch({ route: '/favorites', favorites: [TOKYO.id] });

  expect(await app.do('Open', { id: TOKYO.id })).toMatchObject({
    path: '/favorites/1850147',
    name: 'City',
    state: { status: 'loaded', favorite: true },
  });

  await app.do('ToggleFavorite');
  const back = await app.do('Back');
  expect(back).toMatchObject({ path: '/favorites', state: { items: [] } });
  expect(back.env.favorites).toEqual([]);
});

test('天気タブ → お気に入りタブ（タブのタップ）', async () => {
  const app = await launch({ favorites: [TOKYO.id] });
  expect((await app.do('SelectTab', { tab: 'favorites' })).state.items).toHaveLength(1);
});

test('知らないアクションは送れず、送れるアクションを返す', async () => {
  const app = await launch();
  await expect(app.do('ToggleFavorite')).rejects.toThrow('Weather に ToggleFavorite はない: Appear, Retry, OpenSettings, OpenSearch, SelectTab');
});

test('入力の形が違うアクションは、画面に届く前に断る', async () => {
  const app = await launch({ route: '/search' });
  await expect(app.do('Open', { id: 'tokyo' })).rejects.toThrow('Open の入力が正しくない');
});
