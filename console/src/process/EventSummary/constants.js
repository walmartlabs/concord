export const statusFilterKeys = ['ALL', 'FAILURES', 'OK', 'CHANGED', 'SKIPPED', 'UNREACHABLE'];

export const statusColors = {
    OK: '#5DB571', // green
    CHANGED: '#00A4D3', // blue
    FAILURES: '#EC6357', // red
    UNREACHABLE: '#BDB9B9', // gray
    SKIPPED: '#F6BC32', // yellow
    DEFAULT: '#3F3F3D', // black
    ALL: '#3F3F3D'
};

export const semanticStatusColors = {
    OK: 'green',
    CHANGED: 'blue',
    FAILURES: 'red',
    UNREACHABLE: 'gray',
    SKIPPED: 'orange',
    DEFAULT: 'black'
};
