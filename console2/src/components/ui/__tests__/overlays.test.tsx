import React, { useState } from 'react';
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { Confirm, Dropdown, Modal, Popup } from '@/components/ui';
import { instance as eventStack } from '@/lib/event-stack';

async function letTransitionsFinish() {
    await waitFor(expectBodyClean);
}

function expectBodyClean() {
    expect(document.body.classList.contains('dimmed')).toBe(false);
    expect(document.body.classList.contains('scrolling')).toBe(false);
}


function ModalHarness() {
    const [open, setOpen] = useState(true);
    const [selected, setSelected] = useState('alpha');

    return (
        <>
            <button onClick={() => setOpen(true)}>Reopen modal</button>
            <output aria-label="selected option">{selected}</output>
            <Modal open={open} onClose={() => setOpen(false)}>
                <Modal.Header>Overlay dialog</Modal.Header>
                <Modal.Content>
                    <button>Inside modal</button>
                    <Dropdown
                        aria-label="Choose option"
                        selection
                        search
                        value={selected}
                        options={[
                            { key: 'alpha', text: 'Alpha', value: 'alpha' },
                            { key: 'beta', text: 'Beta', value: 'beta' }
                        ]}
                        onChange={(_, data) => setSelected(data.value as string)}
                    />
                    <Popup
                        trigger={<button>Open popup</button>}
                        content={<button>Popup action</button>}
                        on="click"
                    />
                </Modal.Content>
            </Modal>
        </>
    );
}

afterEach(async () => {
    cleanup();
    await letTransitionsFinish();
    expectBodyClean();
});

describe('local Semantic UI overlays', () => {
    it('keeps Escape priority stable when an earlier modal rerenders', async () => {
        function SiblingModals() {
            const [firstRevision, setFirstRevision] = useState(0);
            const [firstOpen, setFirstOpen] = useState(true);
            const [secondOpen, setSecondOpen] = useState(true);

            return (
                <>
                    <button onClick={() => setFirstRevision((revision) => revision + 1)}>Rerender first</button>
                    <Modal open={firstOpen} className={`first-${firstRevision}`} onClose={() => setFirstOpen(false)}>
                        First modal
                    </Modal>
                    <Modal open={secondOpen} onClose={() => setSecondOpen(false)}>
                        Second modal
                    </Modal>
                </>
            );
        }

        const { getByText, queryByText } = render(<SiblingModals />);
        fireEvent.click(getByText('Rerender first'));
        fireEvent.keyDown(document, { key: 'Escape', keyCode: 27, which: 27 });

        await waitFor(() => expect(queryByText('Second modal')).toBeNull());
        expect(queryByText('First modal')).toBeTruthy();
    });

    it('removes each closed modal dimmer from the shared event stack', async () => {
        function ReopeningModal() {
            const [open, setOpen] = useState(true);
            return (
                <>
                    <button onClick={() => setOpen(true)}>Reopen</button>
                    <Modal open={open} onClose={() => setOpen(false)}>Tracked modal</Modal>
                </>
            );
        }

        const eventStackTargets: unknown = Reflect.get(eventStack, 'targets');
        if (!(eventStackTargets instanceof Map)) throw new Error('Event stack targets are unavailable');
        const targets = eventStackTargets as Map<EventTarget, unknown>;
        const { getByText, queryByText } = render(<ReopeningModal />);
        for (let cycle = 0; cycle < 2; cycle += 1) {
            const dimmer = document.querySelector('.ui.modals.dimmer')!;
            fireEvent.click(dimmer);
            await waitFor(() => expect(queryByText('Tracked modal')).toBeNull());
            await letTransitionsFinish();
            expect(targets.has(dimmer)).toBe(false);
            if (cycle === 0) fireEvent.click(getByText('Reopen'));
        }
    });

    it('resolves DOM nodes for class and function modal shells', () => {
        class ClassShell extends React.Component<React.HTMLAttributes<HTMLDivElement>> {
            render() {
                return <div {...this.props} />;
            }
        }
        function FunctionShell(props: React.HTMLAttributes<HTMLDivElement>) {
            return <div {...props} />;
        }

        const { getByText, queryByText } = render(
            <>
                <Modal open as={ClassShell}>Class shell content</Modal>
                <Modal open as={FunctionShell}>Function shell content</Modal>
            </>,
        );
        fireEvent.click(getByText('Class shell content'));
        fireEvent.click(getByText('Function shell content'));
        expect(queryByText('Class shell content')).toBeTruthy();
        expect(queryByText('Function shell content')).toBeTruthy();
    });

    it('keeps the modal open for interactions inside its dropdown and popup', async () => {
        const { getByText, getByRole, getByLabelText } = render(<ModalHarness />);

        fireEvent.click(getByText('Inside modal'));
        fireEvent.click(getByRole('combobox', { name: 'Choose option' }));
        fireEvent.click(getByRole('option', { name: 'Beta' }));
        expect(getByLabelText('selected option').textContent).toBe('beta');
        expect(getByText('Overlay dialog')).toBeTruthy();

        fireEvent.click(getByText('Open popup'));
        expect(await waitFor(() => getByText('Popup action'))).toBeTruthy();
        fireEvent.click(getByText('Popup action'));
        expect(getByText('Overlay dialog')).toBeTruthy();
    });

    it('closes on outside click and removes dimmer and scrolling state on reopen and unmount', async () => {
        const { getByText, queryByText, unmount } = render(<ModalHarness />);

        fireEvent.click(document.querySelector('.ui.modals.dimmer')!);
        await waitFor(() => expect(queryByText('Overlay dialog')).toBeNull());
        await letTransitionsFinish();
        expectBodyClean();

        fireEvent.click(getByText('Reopen modal'));
        expect(getByText('Overlay dialog')).toBeTruthy();
        unmount();
        await letTransitionsFinish();
        expectBodyClean();
    });

    it('preserves the baseline where Escape closes an open nested dropdown and its modal', async () => {
        const { getByRole, queryByText } = render(<ModalHarness />);

        fireEvent.click(getByRole('combobox', { name: 'Choose option' }));
        fireEvent.keyDown(document, { key: 'Escape', keyCode: 27, which: 27 });

        await waitFor(() => expect(queryByText('Overlay dialog')).toBeNull());
        await letTransitionsFinish();
        expectBodyClean();
    });

    it('cancels without running the operation and confirms it exactly once', async () => {
        function ConfirmHarness() {
            const [open, setOpen] = useState(true);
            const [operations, setOperations] = useState(0);

            return (
                <>
                    <output aria-label="operation count">{operations}</output>
                    <Confirm
                        open={open}
                        header="Confirm operation"
                        content="Run it?"
                        onCancel={() => setOpen(false)}
                        onConfirm={() => {
                            setOperations((count) => count + 1);
                            setOpen(false);
                        }}
                    />
                    {!open && <button onClick={() => setOpen(true)}>Open confirmation</button>}
                </>
            );
        }

        const { getByText, getByLabelText, queryByText } = render(<ConfirmHarness />);
        fireEvent.click(getByText('Cancel'));
        await waitFor(() => expect(queryByText('Confirm operation')).toBeNull());
        expect(getByLabelText('operation count').textContent).toBe('0');
        await letTransitionsFinish();
        expectBodyClean();

        fireEvent.click(getByText('Open confirmation'));
        fireEvent.click(getByText('OK'));
        await waitFor(() => expect(queryByText('Confirm operation')).toBeNull());
        expect(getByLabelText('operation count').textContent).toBe('1');
        await letTransitionsFinish();
        expectBodyClean();
    });
});
