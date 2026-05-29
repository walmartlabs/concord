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

interface CronField {
    values: Set<number>;
    wildcard: boolean;
}

interface CronExpression {
    minute: CronField;
    hour: CronField;
    dayOfMonth: CronField;
    month: CronField;
    dayOfWeek: CronField;
}

interface FutureMatchOptions {
    matchCount?: number;
    now?: Date;
    timeZone?: string;
}

const aliases: Record<string, string> = {
    '@yearly': '0 0 1 1 *',
    '@annually': '0 0 1 1 *',
    '@monthly': '0 0 1 * *',
    '@weekly': '0 0 * * 0',
    '@daily': '0 0 * * *',
    '@midnight': '0 0 * * *',
    '@hourly': '0 * * * *',
};

const monthNames: Record<string, number> = {
    JAN: 1,
    FEB: 2,
    MAR: 3,
    APR: 4,
    MAY: 5,
    JUN: 6,
    JUL: 7,
    AUG: 8,
    SEP: 9,
    OCT: 10,
    NOV: 11,
    DEC: 12,
};

const dayNames: Record<string, number> = {
    SUN: 0,
    MON: 1,
    TUE: 2,
    WED: 3,
    THU: 4,
    FRI: 5,
    SAT: 6,
};

interface DateParts {
    minute: number;
    hour: number;
    dayOfMonth: number;
    month: number;
    dayOfWeek: number;
}

const formatterCache = new Map<string, Intl.DateTimeFormat>();

const normalizeDayOfWeek = (value: number) => (value === 7 ? 0 : value);

const getFormatter = (timeZone: string) => {
    let formatter = formatterCache.get(timeZone);
    if (!formatter) {
        formatter = new Intl.DateTimeFormat('en-US-u-ca-gregory', {
            timeZone,
            weekday: 'short',
            month: 'numeric',
            day: 'numeric',
            hour: 'numeric',
            minute: 'numeric',
            hourCycle: 'h23',
        });
        formatterCache.set(timeZone, formatter);
    }

    return formatter;
};

const getDateParts = (date: Date, timeZone: string): DateParts => {
    if (timeZone === 'UTC') {
        return {
            minute: date.getUTCMinutes(),
            hour: date.getUTCHours(),
            dayOfMonth: date.getUTCDate(),
            month: date.getUTCMonth() + 1,
            dayOfWeek: date.getUTCDay(),
        };
    }

    const parts = getFormatter(timeZone).formatToParts(date);
    const getPart = (type: Intl.DateTimeFormatPartTypes) =>
        parts.find((part) => part.type === type)?.value;

    const weekday = getPart('weekday')?.toUpperCase().slice(0, 3);
    const dayOfWeek = weekday ? dayNames[weekday] : undefined;

    if (dayOfWeek === undefined) {
        throw new Error(`Unable to read day of week for timezone: ${timeZone}`);
    }

    return {
        minute: Number(getPart('minute')),
        hour: Number(getPart('hour')),
        dayOfMonth: Number(getPart('day')),
        month: Number(getPart('month')),
        dayOfWeek,
    };
};

const parseValue = (value: string, min: number, max: number, names?: Record<string, number>) => {
    const parsed = names?.[value] ?? Number(value);

    if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
        throw new Error(`Invalid cron value: ${value}`);
    }

    return parsed;
};

const fullValues = (min: number, max: number, normalize: (value: number) => number) => {
    const result = new Set<number>();

    for (let value = min; value <= max; value++) {
        result.add(normalize(value));
    }

    return result;
};

const isFullSet = (values: Set<number>, full: Set<number>) => {
    if (values.size !== full.size) {
        return false;
    }

    for (const value of full) {
        if (!values.has(value)) {
            return false;
        }
    }

    return true;
};

const parseField = (
    field: string,
    min: number,
    max: number,
    names?: Record<string, number>,
    normalize: (value: number) => number = (value) => value
): CronField => {
    const values = new Set<number>();
    const full = fullValues(min, max, normalize);
    let wildcard = false;

    for (const rawPart of field.toUpperCase().split(',')) {
        const part = rawPart.trim();
        const [rangePart, stepPart] = part.split('/');
        const step = stepPart === undefined ? 1 : Number(stepPart);

        if (!Number.isInteger(step) || step < 1) {
            throw new Error(`Invalid cron step: ${part}`);
        }

        let start: number;
        let end: number;

        if (rangePart === '*' || rangePart === '?') {
            wildcard = wildcard || step === 1;
            start = min;
            end = max;
        } else if (rangePart.includes('-')) {
            const [startPart, endPart] = rangePart.split('-');
            start = parseValue(startPart, min, max, names);
            end = parseValue(endPart, min, max, names);
        } else {
            start = parseValue(rangePart, min, max, names);
            end = stepPart === undefined ? start : max;
        }

        if (start > end) {
            throw new Error(`Invalid cron range: ${part}`);
        }

        for (let value = start; value <= end; value += step) {
            values.add(normalize(value));
        }
    }

    return { values, wildcard: wildcard || isFullSet(values, full) };
};

const parseCronExpression = (expression: string): CronExpression => {
    const normalized = aliases[expression.trim().toLowerCase()] ?? expression;
    const parts = normalized.trim().split(/\s+/);

    if (parts.length !== 5) {
        throw new Error(`Invalid cron expression: ${expression}`);
    }

    return {
        minute: parseField(parts[0], 0, 59),
        hour: parseField(parts[1], 0, 23),
        dayOfMonth: parseField(parts[2], 1, 31),
        month: parseField(parts[3], 1, 12, monthNames),
        dayOfWeek: parseField(parts[4], 0, 7, dayNames, normalizeDayOfWeek),
    };
};

const matchesDay = (parts: DateParts, expression: CronExpression) => {
    const dayOfMonthMatches = expression.dayOfMonth.values.has(parts.dayOfMonth);
    const dayOfWeekMatches = expression.dayOfWeek.values.has(parts.dayOfWeek);

    if (expression.dayOfMonth.wildcard && expression.dayOfWeek.wildcard) {
        return true;
    }

    if (expression.dayOfMonth.wildcard) {
        return dayOfWeekMatches;
    }

    if (expression.dayOfWeek.wildcard) {
        return dayOfMonthMatches;
    }

    return dayOfMonthMatches || dayOfWeekMatches;
};

const matches = (date: Date, expression: CronExpression, timeZone: string) => {
    const parts = getDateParts(date, timeZone);

    return (
        expression.minute.values.has(parts.minute) &&
        expression.hour.values.has(parts.hour) &&
        expression.month.values.has(parts.month) &&
        matchesDay(parts, expression)
    );
};

const formatMatch = (date: Date) => date.toISOString().replace('.000Z', 'Z');

export const getFutureCronMatches = (
    expression?: string,
    { matchCount = 1, now = new Date(), timeZone = 'UTC' }: FutureMatchOptions = {}
): string[] => {
    if (!expression || matchCount < 1) {
        return [];
    }

    let parsed: CronExpression;

    try {
        parsed = parseCronExpression(expression);
    } catch (e) {
        console.warn('Unable to parse cron expression:', expression, e);
        return [];
    }

    const result = [];
    const cursor = new Date(now);
    cursor.setUTCSeconds(0, 0);
    cursor.setUTCMinutes(cursor.getUTCMinutes() + 1);

    const maxAttempts = 5 * 366 * 24 * 60;

    try {
        for (let attempts = 0; attempts < maxAttempts && result.length < matchCount; attempts++) {
            if (matches(cursor, parsed, timeZone)) {
                result.push(formatMatch(cursor));
            }

            cursor.setUTCMinutes(cursor.getUTCMinutes() + 1);
        }
    } catch (e) {
        console.warn('Unable to match cron expression:', expression, e);
        return [];
    }

    return result;
};
