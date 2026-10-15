import { act } from '@testing-library/react';
import { StrictMode } from 'react';
import type { ReactElement } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, test, vi } from 'vitest';
import Sticky from '@/components/ui/modules/Sticky';
import Transition from '@/components/ui/modules/Transition';

declare global {
  // React's `act()` checks this flag; it is absent from the installed React types.
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined
}

// `createRoot` + `act` is used directly (RTL 12's render uses a legacy root),
// so the act environment flag must be set explicitly.
globalThis.IS_REACT_ACT_ENVIRONMENT = true

function renderInStrictMode(tree: ReactElement): { container: HTMLDivElement; unmount: () => void } {
    const container = document.createElement('div')
    document.body.appendChild(container)
    const root = createRoot(container)

    act(() => {
        root.render(<StrictMode>{tree}</StrictMode>)
    })

    return {
        container,
        unmount: () => act(() => root.unmount()),
    }
}

describe('StrictMode effect replay', () => {
    afterEach(() => {
        vi.useRealTimers()
        vi.restoreAllMocks()
        document.body.innerHTML = ''
    })

    test('Transition re-schedules its status-advance timer after replay', () => {
        vi.useFakeTimers()
        const events: string[] = []

        const { container, unmount } = renderInStrictMode(
            <Transition
                transitionOnMount
                visible
                animation="fade"
                duration={300}
                onStart={() => events.push('start')}
                onComplete={() => events.push('complete')}
            >
                <div />
            </Transition>,
        )

        act(() => {
            vi.advanceTimersByTime(1000)
        })

        expect(container.querySelector('div')!.className).not.toContain('animating')
        expect(events).toContain('complete')

        unmount()
    })

    test('Sticky re-schedules its animation frame after replay', () => {
        const requestAnimationFrame = vi.fn(() => 1)
        const cancelAnimationFrame = vi.fn()
        vi.stubGlobal('requestAnimationFrame', requestAnimationFrame)
        vi.stubGlobal('cancelAnimationFrame', cancelAnimationFrame)

        const { unmount } = renderInStrictMode(
            <Sticky>
                <div />
            </Sticky>,
        )

        // Once for the initial mount and once more after the replay re-arms the
        // frame gate. A stale gate would skip the second schedule.
        expect(requestAnimationFrame).toHaveBeenCalledTimes(2)

        unmount()
    })
})
