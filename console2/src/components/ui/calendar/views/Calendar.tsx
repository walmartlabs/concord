import React, { forwardRef } from 'react'
import Table from '@/components/ui/collections/Table/Table'
import type { TableProps } from '@/components/ui/collections/Table/Table'

interface CalendarProps extends React.HTMLAttributes<HTMLTableElement> {
  /** Table content. */
  children?: React.ReactNode
}

/** Legacy calendar prop accepted from untyped callers; it never reaches the table. */
type DroppedCalendarProp = 'outlineOnFocus'

/** Props of the `<table>` this component renders through `Table`'s `as` prop. */
interface CalendarTableProps extends React.TableHTMLAttributes<HTMLTableElement> {
  /** Ref of the `Calendar` element, passed down to the rendered `<table>`. */
  calendarRef?: React.Ref<HTMLElement>
}

/**
 * Renders the calendar table. `Table` renders a function component that does not
 * forward refs, so the calendar ref is passed through `Table`'s `as` element to
 * reach the DOM node.
 */
function CalendarTable({ calendarRef, ...rest }: CalendarTableProps): React.ReactElement {
  return React.createElement('table', {
    ...rest,
    ref: calendarRef as React.Ref<HTMLTableElement>,
  })
}

/** The calendar table. Its ref exposes the rendered `<table>` element. */
function Calendar(props: CalendarProps, ref: React.Ref<HTMLElement>): React.ReactElement {
  const { children, ...rest } = props as CalendarProps & Record<DroppedCalendarProp, unknown>

  const style = { width: '100%', minWidth: '22em', outline: 'none' }

  return React.createElement(
    Table,
    {
      as: CalendarTable,
      calendarRef: ref,
      style,
      unstackable: true,
      celled: true,
      ...(rest as unknown as Partial<TableProps>),
      textAlign: 'center',
    } as TableProps,
    children,
  )
}

export default forwardRef<HTMLElement, CalendarProps>(Calendar)
