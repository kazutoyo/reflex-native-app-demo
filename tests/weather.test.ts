import { expect, test } from 'bun:test';

import { launch } from './sdk';

test('with permission, launching shows the weather for the current location', async () => {
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

test('after a denial, the app can ask for permission again', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: true } });
  expect((await app.ls()).state).toEqual({ status: 'permissionDenied', canAskAgain: true });

  await app.env({ location: { granted: true } }); // The user granted permission
  expect((await app.do('Retry')).state.status).toBe('loaded');
});

test('when the app cannot ask again, it opens Settings', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  expect((await app.ls()).state).toEqual({ status: 'permissionDenied', canAskAgain: false });
  expect((await app.do('OpenSettings')).env.settingsOpened).toBe(1);
});

test('after a denial, the user can go to city search instead', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  expect(await app.do('OpenSearch')).toMatchObject({ path: '/search', name: 'Search' });
});

test('an error when the location is unavailable', async () => {
  const app = await launch({ location: { positionError: 'Cannot get the location' } });
  expect((await app.ls()).state).toEqual({ status: 'error', message: 'Cannot get the location' });
});

test('an API 500 shows an error, and retry loads once it recovers', async () => {
  const app = await launch({ http: { status: 500 } });
  expect((await app.ls()).state).toEqual({ status: 'error', message: 'HTTP 500' });

  await app.env({ http: 'fixtures' });
  expect((await app.do('Retry')).state.status).toBe('loaded');
});

test('granting permission in Settings and returning to the weather tab reloads', async () => {
  const app = await launch({ location: { granted: false, canAskAgain: false } });
  await app.do('OpenSettings');
  await app.do('OpenSearch'); // Leave the weather tab so that coming back sends Appear

  await app.env({ location: { granted: true } }); // Granted in the Settings app
  expect(await app.do('SelectTab', { tab: 'weather' })).toMatchObject({
    name: 'Weather',
    state: { status: 'loaded' },
  });
});

test('once loaded, switching tabs does not refetch', async () => {
  const app = await launch();
  await app.do('OpenSearch');
  const { env } = await app.do('SelectTab', { tab: 'weather' });
  expect(env.httpRequests).toBe(1);
});

test('the weather still shows when the place name is unavailable', async () => {
  const app = await launch({ location: { placeError: 'Reverse geocoding failed' } });
  expect((await app.ls()).state).toMatchObject({ status: 'loaded', place: null });
});
