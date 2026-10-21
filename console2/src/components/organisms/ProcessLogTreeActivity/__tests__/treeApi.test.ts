import { expect, test, vi } from 'vitest';
import { fetchJson } from '../../../../api/common';
import { treeApi } from '../treeApi';

vi.mock('../../../../api/common', async (importOriginal) => ({
    ...(await importOriginal<typeof import('../../../../api/common')>()),
    fetchJson: vi.fn().mockResolvedValue({ items: [], ancestors: [], next: false }),
}));

test('children and global search use separate endpoints', async () => {
    await treeApi.page('process', { parentId: 42, afterId: 50 });
    const childrenUrl = String(vi.mocked(fetchJson).mock.calls.slice(-1)[0][0]);
    expect(childrenUrl.split('?')[0]).toBe('/api/v2/process/process/log/tree');
    expect(new URLSearchParams(childrenUrl.split('?')[1]).get('parentId')).toBe('42');

    await treeApi.search('process', { query: 'deploy', category: 'FAILED', reverse: true });
    const searchUrl = String(vi.mocked(fetchJson).mock.calls.slice(-1)[0][0]);
    expect(searchUrl.split('?')[0]).toBe('/api/v2/process/process/log/tree/search');
    const params = new URLSearchParams(searchUrl.split('?')[1]);
    expect(params.get('query')).toBe('deploy');
    expect(params.get('category')).toBe('FAILED');
    expect(params.has('parentId')).toBe(false);
});
