import { deepMerge } from '../common';

test('deepMerge works', () => {
    const actual = deepMerge({ x: { y: 123 } }, { x: { z: 234 } });
    const expected = { x: { y: 123, z: 234 } };
    expect(actual).toEqual(expected);
});
