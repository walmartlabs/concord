import React, { useState } from 'react';
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { Dropdown, DropdownItemProps, Menu } from '@/components/ui';
import getMenuOptions from '../modules/Dropdown/utils/getMenuOptions';

afterEach(() => cleanup());

type Option = { text: string; value: string };

function ControlledDropdown({
    disabled = false,
    initialValue = []
}: {
    disabled?: boolean;
    initialValue?: string[];
}) {
    const [value, setValue] = useState<string[]>(initialValue);
    const [options, setOptions] = useState<Option[]>([
        { text: 'Alpha', value: 'a' },
        { text: 'Beta', value: 'b' }
    ]);

    return (
        <>
            <Dropdown
                aria-label="Choices"
                name="choices"
                allowAdditions={true}
                clearable={true}
                disabled={disabled}
                multiple={true}
                onAddItem={(_event, data) => {
                    const addedValue = String(data.value);
                    setOptions((current) => [
                        ...current,
                        { text: addedValue, value: addedValue }
                    ]);
                }}
                onChange={(_event, data) => setValue(data.value as string[])}
                options={options}
                search={true}
                selection={true}
                value={value}
            />
            <output data-testid="selected-values">{JSON.stringify(value)}</output>
        </>
    );
}

describe('controlled Dropdown selection', () => {
    test('selects by mouse and keyboard, persists an addition, removes labels, and clears', () => {
        const { container } = render(<ControlledDropdown />);
        const dropdown = container.querySelector('[name="choices"]') as HTMLElement;

        fireEvent.click(dropdown);
        fireEvent.click(within(dropdown).getByRole('option', { name: 'Alpha' }));
        expect(within(container).getByTestId('selected-values').textContent).toBe('["a"]');
        expect(within(dropdown).getByText('Alpha', { selector: '.ui.label' })).toBeTruthy();

        const input = container.querySelector('input.search') as HTMLInputElement;
        fireEvent.change(input, { target: { value: 'Beta' } });
        fireEvent.keyDown(input, { key: 'ArrowDown', keyCode: 40, which: 40 });
        fireEvent.keyDown(input, { key: 'Enter', keyCode: 13, which: 13 });
        expect(within(container).getByTestId('selected-values').textContent).toBe('["a","b"]');
        expect(within(dropdown).getByText('Beta', { selector: '.ui.label' })).toBeTruthy();

        fireEvent.change(input, { target: { value: 'Gamma' } });
        fireEvent.keyDown(input, { key: 'ArrowDown', keyCode: 40, which: 40 });
        fireEvent.keyDown(input, { key: 'Enter', keyCode: 13, which: 13 });
        expect(within(container).getByTestId('selected-values').textContent).toBe('["a","b","Gamma"]');
        expect(within(dropdown).getByText('Gamma', { selector: '.ui.label' })).toBeTruthy();

        const alphaLabel = within(dropdown).getByText('Alpha', { selector: '.ui.label' });
        const removeAlpha = alphaLabel.querySelector('.delete.icon') as HTMLElement | null;
        expect(removeAlpha).toBeTruthy();
        fireEvent.click(removeAlpha as HTMLElement);
        expect(within(container).getByTestId('selected-values').textContent).toBe('["b","Gamma"]');
        expect(within(dropdown).queryByText('Alpha', { selector: '.ui.label' })).toBeNull();

        fireEvent.click(dropdown.querySelector('.clear.icon') as HTMLElement);
        expect(within(container).getByTestId('selected-values').textContent).toBe('[]');
        expect(within(dropdown).queryByText('Beta', { selector: '.ui.label' })).toBeNull();
        expect(within(dropdown).queryByText('Gamma', { selector: '.ui.label' })).toBeNull();

        fireEvent.change(input, { target: { value: 'Gamma' } });
        fireEvent.click(within(dropdown).getByRole('option', { name: 'Gamma' }));
        expect(within(container).getByTestId('selected-values').textContent).toBe('["Gamma"]');
        expect(within(dropdown).getByText('Gamma', { selector: '.ui.label' })).toBeTruthy();
    });

    test('does not change selection when disabled', () => {
        const { container } = render(<ControlledDropdown disabled={true} initialValue={['a']} />);
        const dropdown = container.querySelector('.ui.dropdown') as HTMLElement;

        fireEvent.click(dropdown);
        fireEvent.keyDown(dropdown, { key: 'ArrowDown', keyCode: 40, which: 40 });
        fireEvent.keyDown(dropdown, { key: 'Enter', keyCode: 13, which: 13 });

        expect(within(container).getByTestId('selected-values').textContent).toBe('["a"]');
        expect(within(dropdown).getByText('Alpha', { selector: '.ui.label' })).toBeTruthy();
        expect(within(dropdown).queryByText('Beta', { selector: '.ui.label' })).toBeNull();
    });

    test('keeps custom action items when no options array is supplied', () => {
        function ActionMenu() {
            const [detailsOpen, setDetailsOpen] = useState(false);
            return (
                <>
                    <Menu>
                        <Menu.Item as={Dropdown} text="System">
                            <Dropdown.Menu>
                                <Dropdown.Item text="About" onClick={() => setDetailsOpen(true)} />
                            </Dropdown.Menu>
                        </Menu.Item>
                    </Menu>
                    {detailsOpen && <section aria-label="About details">Application details</section>}
                </>
            );
        }

        const { getByRole, getByText } = render(<ActionMenu />);
        const dropdown = getByRole('listbox');
        expect(getByText('System').textContent).toBe('System');
        fireEvent.click(dropdown);
        expect(dropdown.getAttribute('aria-expanded')).toBe('true');
        fireEvent.click(getByRole('option', { name: 'About' }));
        expect(getByRole('region', { name: 'About details' }).textContent).toBe('Application details');
    });
});

describe('Dropdown regressions', () => {
    test('resolves a custom root to its DOM node when opening the menu', () => {
        class CustomRoot extends React.Component<React.HTMLAttributes<HTMLDivElement>> {
            render() {
                return <div {...this.props} />;
            }
        }

        const { container } = render(
            <Dropdown as={CustomRoot} selection options={[{ text: 'Alpha', value: 'a' }]} />
        );
        const dropdown = container.querySelector('.ui.dropdown') as HTMLElement;

        fireEvent.click(dropdown);

        expect(within(dropdown).getByRole('option', { name: 'Alpha' })).toBeTruthy();
    });

    test('custom search runs only for a nonempty query and filters by its result', () => {
        const options: DropdownItemProps[] = [
            { text: 'Alpha', value: 'a' },
            { text: 'Beta', value: 'b' }
        ];
        const search = vi.fn((items: DropdownItemProps[], query: string) =>
            items.filter((option) => option.text === query)
        );

        expect(getMenuOptions({ options, search, searchQuery: '' })).toEqual(options);
        expect(search).not.toHaveBeenCalled();

        expect(getMenuOptions({ options, search, searchQuery: 'Beta' })).toEqual([options[1]]);
        expect(search).toHaveBeenCalledOnce();
        expect(search).toHaveBeenCalledWith(options, 'Beta');
    });

    test('deburrs query and option text when enabled', () => {
        const options: DropdownItemProps[] = [
            { text: 'Café', value: 'accented' },
            { text: 'Cafe', value: 'plain' },
            { text: 'Tea', value: 'tea' }
        ];

        expect(getMenuOptions({ options, search: true, searchQuery: 'cafe', deburr: true }))
            .toEqual([options[0], options[1]]);
        expect(getMenuOptions({ options, search: true, searchQuery: 'café', deburr: true }))
            .toEqual([options[0], options[1]]);
        expect(getMenuOptions({ options, search: true, searchQuery: 'cafe', deburr: false }))
            .toEqual([options[1]]);
    });

    test('adds ReactNode labels and appends bottom additions without mutating options', () => {
        const options: DropdownItemProps[] = [
            { text: 'Alpha', value: 'a' },
            { text: 'Beta', value: 'b' }
        ];
        const label = <i>Insert</i>;
        const result = getMenuOptions({
            options,
            search: () => options,
            searchQuery: 'Gamma',
            allowAdditions: true,
            additionLabel: label,
            additionPosition: 'bottom'
        });
        const addition = result[2];
        const additionText = addition.text as React.ReactNode[];

        expect(result.slice(0, 2)).toEqual(options);
        expect(addition.key).toBe('addition');
        expect(additionText[0]).toEqual(expect.objectContaining({ type: 'i', key: 'addition-label' }));
        expect(additionText[1]).toEqual(expect.objectContaining({ type: 'b', key: 'addition-query', props: { children: 'Gamma' } }));
        expect(options).toEqual([
            { text: 'Alpha', value: 'a' },
            { text: 'Beta', value: 'b' }
        ]);
    });

    test('preserves explicitly empty addition labels', () => {
        const options: DropdownItemProps[] = [];
        const addition = (additionLabel: React.ReactNode) =>
            getMenuOptions({
                options,
                search: true,
                searchQuery: 'Gamma',
                allowAdditions: true,
                additionLabel
            })[0];

        expect((addition(null).text as React.ReactNode[])[0]).toBeNull();
        expect((addition('').text as React.ReactNode[])[0]).toBe('');
    });
});
