import React, { createRef } from 'react';
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { afterEach, describe, expect, test } from 'vitest';
import { Checkbox, Dropdown } from '@/components/ui';

afterEach(() => cleanup());

describe('auto-controlled state', () => {
    test('uncontrolled Checkbox toggles exactly once via the label', () => {
        const changes: { checked: boolean; indeterminate: boolean }[] = [];
        const { container } = render(
            <Checkbox
                defaultChecked
                id="cb"
                label="Toggle me"
                onChange={(_e, data) =>
                    changes.push({ checked: data.checked, indeterminate: data.indeterminate })
                }
            />
        );

        const checkbox = container.querySelector('.ui.checkbox') as HTMLElement;
        const input = checkbox.querySelector('input') as HTMLInputElement;
        const label = within(checkbox).getByText('Toggle me');

        expect(input.checked).toBe(true);

        fireEvent.mouseDown(label);
        fireEvent.mouseUp(label);
        fireEvent.click(label);

        expect(input.checked).toBe(false);
        expect(changes).toEqual([{ checked: false, indeterminate: false }]);
    });
    test('preserves a custom label ref and toggles exactly once', () => {
        const labelRef = createRef<HTMLLabelElement>();
        const changes: boolean[] = [];
        const { container } = render(
            <Checkbox
                id="custom-cb"
                label={<label ref={labelRef} htmlFor="custom-cb">Custom label</label>}
                onChange={(_e, data) => changes.push(data.checked)}
            />
        );
        const label = within(container).getByText('Custom label');
        const input = container.querySelector('input') as HTMLInputElement;

        expect(labelRef.current).toBe(label);

        fireEvent.mouseDown(label);
        fireEvent.mouseUp(label);
        fireEvent.click(label);

        expect(input.checked).toBe(true);
        expect(changes).toEqual([true]);
    });


    test('controlled Checkbox stays parent-controlled and reflects a rerender', () => {
        const changes: { checked: boolean }[] = [];
        const Harness = ({ checked }: { checked: boolean }) => (
            <Checkbox
                checked={checked}
                id="cb"
                label="Controlled"
                onChange={(_e, data) => changes.push({ checked: data.checked })}
            />
        );

        const { container, rerender } = render(<Harness checked={true} />);
        const input = container.querySelector('input') as HTMLInputElement;
        const label = within(container).getByText('Controlled');

        expect(input.checked).toBe(true);

        fireEvent.mouseDown(label);
        fireEvent.mouseUp(label);
        fireEvent.click(label);

        // Controlled: the checkbox keeps the parent's value after interaction.
        expect(input.checked).toBe(true);
        expect(changes).toEqual([{ checked: false }]);

        // A parent rerender to the opposite value is reflected.
        rerender(<Harness checked={false} />);
        expect(input.checked).toBe(false);
    });

    test('uncontrolled Dropdown keeps user selection over a changed defaultValue', () => {
        const options = [
            { text: 'Alpha', value: 'a' },
            { text: 'Beta', value: 'b' }
        ];

        const { container, rerender } = render(
            <Dropdown defaultValue="a" selection options={options} />
        );
        const dropdown = container.querySelector('.ui.dropdown') as HTMLElement;
        const selectedText = () => within(dropdown).getByRole('alert').textContent;

        expect(selectedText()).toBe('Alpha');

        fireEvent.click(dropdown);
        fireEvent.click(within(dropdown).getByRole('option', { name: 'Beta' }));
        expect(selectedText()).toBe('Beta');

        // Changing the default must not overwrite the user's selection.
        rerender(<Dropdown defaultValue="b" selection options={options} />);
        expect(selectedText()).toBe('Beta');
    });
});
