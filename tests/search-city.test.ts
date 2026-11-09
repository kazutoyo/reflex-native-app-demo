import { expect, test } from 'bun:test';

import { launch, TOKYO } from './sdk';

test('searching shows matching cities', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: 'Tokyo' });
  const { state } = await app.do('Submit');
  expect(state.status).toBe('loaded');
  expect(state.results[0]).toEqual(TOKYO);
});

test('zero results is not an error', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: 'zzzzqqq' });
  expect((await app.do('Submit')).state).toMatchObject({ status: 'loaded', results: [] });
});

test('an empty query does not search', async () => {
  const app = await launch({ route: '/search' });
  await app.do('ChangeQuery', { query: '  ' });
  expect((await app.do('Submit')).state.status).toBe('idle');
});

test('favoriting on the detail screen shows up after going back to search', async () => {
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

test('an unknown city has no fixture, so it errors (without reaching the network)', async () => {
  const app = await launch({ route: '/cities/999' });
  const { state } = await app.ls();
  expect(state.status).toBe('error');
  expect(state.message).toContain('No fixture for');
});
