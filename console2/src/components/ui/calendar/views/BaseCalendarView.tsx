import React, { useCallback, useEffect, useRef } from 'react'
import findHTMLElement from '../lib/findHTMLElement'

export interface BaseCalendarViewProps {
  /** Used for passing calendar dom element to parent component. */
  onMount: (e: HTMLElement) => void

  /** Called on calendar blur. */
  onBlur: () => void

  /** An array of values to fill a calendar with (dates, or years, or anything like that). */
  values: string[]

  /** Called after clicking on particular value (date, year or anything like that). */
  onValueClick: (e: React.SyntheticEvent<HTMLElement>, data: OnValueClickData) => void

  /** Called on calendar cell hover. */
  onCellHover: (e: React.SyntheticEvent<HTMLElement>, data: OnCellHoverData) => void

  /** Index of a cell that should be displayed as hovered. */
  hoveredItemIndex?: number

  /** An array of cell positions to display as disabled. */
  disabledItemIndexes?: number[]

  /** Moment date localization */
  localization?: string
}

export interface SingleSelectionCalendarViewProps {
  /** Position of a cell to display as active. */
  activeItemIndex?: number
}

export interface CalendarWithHeaderViewProps {
  /** Called after click on next page button. */
  onNextPageBtnClick: (e?: React.SyntheticEvent<HTMLElement>, data?: unknown, cb?: () => void) => void

  /** Called after click on previous page button. */
  onPrevPageBtnClick: (e?: React.SyntheticEvent<HTMLElement>, data?: unknown, cb?: () => void) => void

  /** Whether to display previous page button as active or disabled. */
  hasPrevPage: boolean

  /** Whether to display next page button as active or disabled. */
  hasNextPage: boolean

  /** Called after click on calendar header. */
  onHeaderClick: () => void
}

export interface HeadingValueProps {
  /** A value (date, year or anything like that) that is displayed in calendar header. */
  currentHeadingValue: string
}

export interface CalendarWithOptionalHeaderViewProps {
  /** Whether a calendar has header. */
  hasHeader: boolean

  /** Called after click on next page button. */
  onNextPageBtnClick?: (e?: React.SyntheticEvent<HTMLElement>, data?: unknown, cb?: () => void) => void

  /** Called after click on previous page button. */
  onPrevPageBtnClick?: (e?: React.SyntheticEvent<HTMLElement>, data?: unknown, cb?: () => void) => void

  /** Whether to display previous page button as active or disabled. */
  hasPrevPage?: boolean

  /** Whether to display next page button as active or disabled. */
  hasNextPage?: boolean

  /** A value (date, year or anything like that) that is displayed in calendar header. */
  currentHeadingValue?: string

  /** Called after click on calendar header. */
  onHeaderClick?: () => void
}

export interface OnValueClickData {
  [key: string]: unknown

  /** Position of the clicked cell. */
  itemPosition: number

  /** Text content of the clicked cell. */
  value: string
}

/** Payload of a cell hover: the hovered cell props plus its position on the page. */
export interface OnCellHoverData {
  /** Position of the hovered cell. */
  itemPosition: number
}

/**
 * Mount behavior shared by the picker view components: it resolves the mounted
 * calendar DOM element and hands it to the parent component. The returned
 * callback is the `ref` of the `Calendar` component.
 */
export function useCalendarView(
  onMount: (calendarNode: HTMLElement) => void,
): React.RefCallback<HTMLElement> {
  const calendarNode = useRef<HTMLElement>()

  const setCalendarNode = useCallback<React.RefCallback<HTMLElement>>((node) => {
    calendarNode.current = findHTMLElement(node)
  }, [])

  useEffect(() => {
    if (onMount) {
      onMount(calendarNode.current)
    }
  }, [])

  return setCalendarNode
}
