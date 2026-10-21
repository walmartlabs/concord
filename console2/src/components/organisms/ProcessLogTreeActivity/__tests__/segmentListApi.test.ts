import { expect, test, vi } from 'vitest';
import { fetchJson } from '../../../../api/common';
import { listLogSegments } from '../../../../api/process/log';

vi.mock('../../../../api/common', async (importOriginal) => ({
    ...(await importOriginal<typeof import('../../../../api/common')>()),
    fetchJson: vi.fn().mockResolvedValue([]),
}));

test('loads all segment metadata through the existing flat API', async () => {
    const signal = new AbortController().signal;
    await listLogSegments('process', 0, -1, signal);
    expect(fetchJson).toHaveBeenCalledWith(
        '/api/v2/process/process/log/segment?offset=0&limit=-1',
        { signal }
    );
});
