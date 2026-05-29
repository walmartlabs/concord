/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

interface AnsiStyle {
    bold?: boolean;
    italic?: boolean;
    underline?: boolean;
    foreground?: string;
    background?: string;
}

const sgrPattern = /\x1b\[([0-9;]*)m/g;
const controlPattern = /\x1b\[[0-?]*[ -/]*[@-~]/g;

const colors = [
    'rgb(0,0,0)',
    'rgb(187,0,0)',
    'rgb(0,187,0)',
    'rgb(187,187,0)',
    'rgb(0,0,187)',
    'rgb(187,0,187)',
    'rgb(0,187,187)',
    'rgb(255,255,255)'
];

const brightColors = [
    'rgb(85,85,85)',
    'rgb(255,85,85)',
    'rgb(0,255,0)',
    'rgb(255,255,85)',
    'rgb(85,85,255)',
    'rgb(255,85,255)',
    'rgb(85,255,255)',
    'rgb(255,255,255)'
];

const cubeValues = [0, 95, 135, 175, 215, 255];

const color256 = (idx: number): string | undefined => {
    if (idx >= 0 && idx <= 7) {
        return colors[idx];
    }

    if (idx >= 8 && idx <= 15) {
        return brightColors[idx - 8];
    }

    if (idx >= 16 && idx <= 231) {
        const value = idx - 16;
        const r = cubeValues[Math.floor(value / 36)];
        const g = cubeValues[Math.floor((value % 36) / 6)];
        const b = cubeValues[value % 6];

        return `rgb(${r},${g},${b})`;
    }

    if (idx >= 232 && idx <= 255) {
        const value = 8 + (idx - 232) * 10;

        return `rgb(${value},${value},${value})`;
    }

    return undefined;
};

const isEmpty = (style: AnsiStyle) =>
    !style.bold && !style.italic && !style.underline && !style.foreground && !style.background;

const toCss = (style: AnsiStyle): string => {
    const css = [];

    if (style.bold) {
        css.push('font-weight:bold');
    }

    if (style.italic) {
        css.push('font-style:italic');
    }

    if (style.underline) {
        css.push('text-decoration:underline');
    }

    if (style.foreground) {
        css.push(`color:${style.foreground}`);
    }

    if (style.background) {
        css.push(`background-color:${style.background}`);
    }

    return css.join(';');
};

const nextValue = (codes: number[], idx: number): number | undefined => codes[idx + 1];

const applyExtendedColor = (
    style: AnsiStyle,
    property: 'foreground' | 'background',
    codes: number[],
    idx: number
): number => {
    const mode = nextValue(codes, idx);

    if (mode === 5) {
        const value = nextValue(codes, idx + 1);
        const color = value !== undefined ? color256(value) : undefined;

        if (color) {
            style[property] = color;
        }

        return idx + 2;
    }

    if (mode === 2) {
        const r = nextValue(codes, idx + 1);
        const g = nextValue(codes, idx + 2);
        const b = nextValue(codes, idx + 3);

        if (r !== undefined && g !== undefined && b !== undefined) {
            style[property] = `rgb(${r},${g},${b})`;
        }

        return idx + 4;
    }

    return idx + 1;
};

const applyCodes = (style: AnsiStyle, codes: number[]) => {
    for (let i = 0; i < codes.length; i++) {
        const code = codes[i];

        if (code === 0) {
            style.bold = false;
            style.italic = false;
            style.underline = false;
            style.foreground = undefined;
            style.background = undefined;
        } else if (code === 1) {
            style.bold = true;
        } else if (code === 3) {
            style.italic = true;
        } else if (code === 4) {
            style.underline = true;
        } else if (code === 22) {
            style.bold = false;
        } else if (code === 23) {
            style.italic = false;
        } else if (code === 24) {
            style.underline = false;
        } else if (code >= 30 && code <= 37) {
            style.foreground = colors[code - 30];
        } else if (code === 38) {
            i = applyExtendedColor(style, 'foreground', codes, i);
        } else if (code === 39) {
            style.foreground = undefined;
        } else if (code >= 40 && code <= 47) {
            style.background = colors[code - 40];
        } else if (code === 48) {
            i = applyExtendedColor(style, 'background', codes, i);
        } else if (code === 49) {
            style.background = undefined;
        } else if (code >= 90 && code <= 97) {
            style.foreground = brightColors[code - 90];
        } else if (code >= 100 && code <= 107) {
            style.background = brightColors[code - 100];
        }
    }
};

export const ansiToHtml = (value: string): string => {
    const style: AnsiStyle = {};
    let result = '';
    let position = 0;
    let spanOpen = false;

    const closeSpan = () => {
        if (spanOpen) {
            result += '</span>';
            spanOpen = false;
        }
    };

    const openSpan = () => {
        if (!isEmpty(style)) {
            result += `<span style="${toCss(style)}">`;
            spanOpen = true;
        }
    };

    for (const match of value.matchAll(sgrPattern)) {
        result += value.substring(position, match.index);
        closeSpan();

        const rawCodes = match[1] ? match[1].split(';').map((v) => Number(v || 0)) : [0];
        applyCodes(style, rawCodes);
        openSpan();

        position = match.index + match[0].length;
    }

    result += value.substring(position);
    closeSpan();

    return result.replace(controlPattern, '');
};
