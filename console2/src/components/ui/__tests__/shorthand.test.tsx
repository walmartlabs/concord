import React from 'react';
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';
import { createShorthand } from '@/components/ui/lib';

type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement>;

afterEach(() => cleanup());

describe('createShorthand', () => {
    test('clones an element merging default/user/override props without mutating the original', () => {
        const originalOnClick = vi.fn();
        const overrideOnClick = vi.fn();
        const original = (
            <button className="user" style={{ color: 'red' }} onClick={originalOnClick}>
                Click
            </button>
        );

        const node = createShorthand<ButtonProps, unknown>('button', null, original, {
            defaultProps: { className: 'default', style: { padding: 4 } },
            overrideProps: { className: 'override', style: { color: 'blue' }, onClick: overrideOnClick }
        }) as React.ReactElement;

        // The original element is untouched.
        expect(original.props.className).toBe('user');
        expect(original.props.style).toEqual({ color: 'red' });

        const { container } = render(node);
        const button = container.querySelector('button') as HTMLButtonElement;

        // Classes are deduplicated in order (default, override, user).
        expect(button.className).toBe('default override user');
        // Styles are merged; override wins for the conflicting key, default fills the rest.
        expect(button.style.color).toBe('blue');
        expect(button.style.padding).toBe('4px');

        fireEvent.click(button);
        expect(overrideOnClick).toHaveBeenCalledTimes(1);
        expect(originalOnClick).not.toHaveBeenCalled();
    });

    test('returns null for null/false shorthand and respects explicit vs generated keys', () => {
        expect(createShorthand<ButtonProps, unknown>('button', null, null)).toBeNull();
        expect(createShorthand<ButtonProps, unknown>('button', null, false)).toBeNull();

        // A primitive value generates its own key.
        const generated = createShorthand<ButtonProps, unknown>('button', null, 'text') as React.ReactElement;
        expect(generated.key).toBe('text');

        // An explicit childKey wins over the generated key.
        const explicit = createShorthand<ButtonProps, unknown>('button', null, 'text', {
            defaultProps: { childKey: 'child' }
        }) as React.ReactElement;
        expect(explicit.key).toBe('child');
    });
});
