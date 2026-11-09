import { expect, test } from 'bun:test';

import { launch, TOKYO } from './sdk';

test('favorites is empty when nothing is saved', async () => {
  const app = await launch({ route: '/favorites' });
  expect(await app.ls()).toMatchObject({ name: 'Favorites', state: { status: 'loaded', items: [] } });
});

test('favorites lists each city with its current weather', async () => {
  const app = await launch({ route: '/favorites', favorites: [TOKYO.id] });
  expect((await app.ls()).state.items).toEqual([
    { city: TOKYO, current: { temperature: 23.5, code: 0 }, failed: false },
  ]);
});

test('a city whose weather fails to load stays in the list', async () => {
  const app = await launch({ route: '/favorites', favorites: [TOKYO.id], http: { status: 503 } });
  expect((await app.ls()).state.items).toEqual([{ city: TOKYO, current: null, failed: true }]);
});

test('unfavoriting a city and going back removes it from the list', async () => {
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

test('weather tab → favorites tab (tapping the tab)', async () => {
  const app = await launch({ favorites: [TOKYO.id] });
  expect((await app.do('SelectTab', { tab: 'favorites' })).state.items).toHaveLength(1);
});

test('an unknown action is rejected with the list of available actions', async () => {
  const app = await launch();
  await expect(app.do('ToggleFavorite')).rejects.toThrow('Weather has no action ToggleFavorite. Actions: Appear, Retry, OpenSettings, OpenSearch, SelectTab');
});

test('an action with the wrong input shape is rejected before it reaches the screen', async () => {
  const app = await launch({ route: '/search' });
  await expect(app.do('Open', { id: 'tokyo' })).rejects.toThrow('Invalid input for Open');
  await expect(app.do('Open', { id: '1850147' })).rejects.toThrow('Invalid input for Open');
});
