import React, { useState } from 'react';
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import moment from 'moment';
import { DateInput, DateTimeInput } from '@/components/ui/calendar';

const DATE_FORMAT = 'YYYY-MM-DD';
const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm';

function ControlledDateInput({ initialValue, clearable = true }: { initialValue: string; clearable?: boolean }) {
    const [value, setValue] = useState(initialValue);
    return (
        <DateInput
            value={value}
            dateFormat={DATE_FORMAT}
            clearable={clearable}
            animation="none"
            duration={0}
            onChange={(_event, data) => setValue(data.value)}
        />
    );
}

function ControlledDateTimeInput({ initialValue, clearable = true }: { initialValue: string; clearable?: boolean }) {
    const [value, setValue] = useState(initialValue);
    return (
        <DateTimeInput
            value={value}
            dateTimeFormat={DATE_TIME_FORMAT}
            clearable={clearable}
            animation="none"
            duration={0}
            onChange={(_event, data) => setValue(data.value)}
        />
    );
}

afterEach(() => cleanup());

function inputOf(container: HTMLElement): HTMLInputElement {
    const input = container.querySelector('input');
    if (!input) throw new Error('Expected a rendered calendar input');
    return input;
}

function renderedCell(label: string): HTMLElement {
    const candidates = Array.from(document.body.querySelectorAll<HTMLElement>('td, [role="gridcell"], button'));
    const cell = candidates.find((element) => (element.textContent ?? '').trim().replace(/^0+(?=\d)/, '') === label.replace(/^0+(?=\d)/, ''));
    if (!cell) throw new Error(`Could not find rendered calendar cell ${label}`);
    return cell;
}

async function clickRenderedCell(label: string) {
    const cell = await waitFor(() => renderedCell(label));
    await act(async () => {
        fireEvent.click(cell);
    });
}

describe('controlled calendar inputs', () => {
    it('selects a day and clears an optional date value', async () => {
        const { container } = render(<ControlledDateInput initialValue="2019-09-04" />);
        const input = inputOf(container);
        expect(input.value).toBe('2019-09-04');

        fireEvent.click(input);
        await waitFor(() => expect(document.body.textContent).toContain('September'));
        await clickRenderedCell('5');
        await waitFor(() => expect(input.value).toBe('2019-09-05'));

        const clear = container.querySelector<HTMLElement>('.clear.icon, .delete.icon, .close.icon, .remove.icon, [aria-label="Clear"]');
        expect(clear).not.toBeNull();
        await act(async () => {
            fireEvent.click(clear!);
        });
        await waitFor(() => expect(input.value).toBe(''));
    });

    it('selects date, hour and minute and preserves a manually entered offset timestamp', async () => {
        const { container } = render(<ControlledDateTimeInput initialValue="2019-09-04 01:05" />);
        const input = inputOf(container);
        expect(input.value).toBe('2019-09-04 01:05');

        fireEvent.click(input);
        await waitFor(() => expect(document.body.textContent).toContain('September'));
        await clickRenderedCell('5');
        await clickRenderedCell('02:00');
        await clickRenderedCell('02:05');
        await waitFor(() => expect(input.value).toBe('2019-09-05 02:05'));

        const manual = '2019-09-04T01:05:00.000-04:00';
        fireEvent.change(input, { target: { value: manual } });
        fireEvent.blur(input);
        await waitFor(() => expect(input.value).toBe(manual));
    });
    it('uses dateFormat as the date-time format fallback when committing a selection', async () => {
        function ControlledDateTimeFallback() {
            const [value, setValue] = useState('2019/09/04 01:05');
            return (
                <DateTimeInput
                    value={value}
                    dateFormat="YYYY/MM/DD"
                    animation="none"
                    duration={0}
                    onChange={(_event, data) => setValue(data.value)}
                />
            );
        }

        const { container } = render(<ControlledDateTimeFallback />);
        const input = inputOf(container);
        expect(input.value).toBe('2019/09/04 01:05');

        fireEvent.click(input);
        await waitFor(() => expect(document.body.textContent).toContain('September'));
        await waitFor(() => expect(document.body.textContent).toContain('2019'));
        await clickRenderedCell('5');
        await clickRenderedCell('02:00');
        await clickRenderedCell('02:05');
        await waitFor(() => expect(input.value).toBe('2019/09/05 02:05'));
    });

    it('renders the picker using its explicitly registered localization', async () => {
        const previousLocale = moment.locale();
        moment.defineLocale('calendar-test', {
            parentLocale: 'en',
            months: 'January_February_March_April_May_June_July_August_CustomSeptember_October_November_December'.split('_'),
            weekdaysMin: 'D0_D1_D2_D3_D4_D5_D6'.split('_'),
        });
        moment.locale(previousLocale);
        try {
            const { container } = render(
                <DateInput
                    value="2019-09-04"
                    dateFormat={DATE_FORMAT}
                    localization="calendar-test"
                    animation="none"
                    duration={0}
                    onChange={() => {}}
                />
            );
            fireEvent.click(inputOf(container));
            await waitFor(() => expect(document.body.textContent).toContain('CustomSeptember'));
            expect(document.body.textContent).toContain('D1');
        } finally {
            cleanup();
            moment.defineLocale('calendar-test', null);
            moment.locale(previousLocale);
        }
    });

    it('does not render a clear affordance for required date and date-time values', () => {
        const date = render(<ControlledDateInput initialValue="2019-09-04" clearable={false} />);
        expect(inputOf(date.container).value).toBe('2019-09-04');
        expect(date.container.querySelector('.clear.icon, .delete.icon, .close.icon, .remove.icon, [aria-label="Clear"]')).toBeNull();

        cleanup();
        const dateTime = render(
            <ControlledDateTimeInput initialValue="2019-09-04 01:05" clearable={false} />
        );
        expect(inputOf(dateTime.container).value).toBe('2019-09-04 01:05');
        expect(dateTime.container.querySelector('.clear.icon, .delete.icon, .close.icon, .remove.icon, [aria-label="Clear"]')).toBeNull();
    });
    it('stops responding to window scroll after an open calendar is unmounted', async () => {
        let blurs = 0;
        const { container, unmount } = render(
            <DateTimeInput
                value="2019-09-04 01:05"
                dateTimeFormat={DATE_TIME_FORMAT}
                animation="none"
                duration={0}
                onChange={() => {}}
                onBlur={() => { blurs += 1; }}
            />
        );
        const input = inputOf(container);
        fireEvent.click(input);
        await waitFor(() => expect(document.querySelector('.ui.popup:not(.animating)')).not.toBeNull());
        fireEvent.scroll(window);
        await waitFor(() => expect(document.querySelector('.ui.popup')).toBeNull());
        expect(blurs).toBe(1);

        fireEvent.click(input);
        await waitFor(() => expect(document.querySelector('.ui.popup:not(.animating)')).not.toBeNull());
        unmount();
        fireEvent.scroll(window);
        expect(blurs).toBe(1);
    });
});
