import React from 'react'
import {
  BaseCalendarViewProps,
  CalendarWithOptionalHeaderViewProps,
  SingleSelectionCalendarViewProps,
  useCalendarView,
} from './BaseCalendarView'
import Calendar from './Calendar'
import Body from './CalendarBody/Body'
import Header, { HeaderProps } from './CalendarHeader/Header'

export const MONTH_CALENDAR_ROW_WIDTH = 3

type MonthViewProps = BaseCalendarViewProps &
  SingleSelectionCalendarViewProps &
  CalendarWithOptionalHeaderViewProps

/** Legacy calendar prop accepted from untyped callers; it never reaches the table. */
type DroppedViewProp = 'inline'

function MonthView(props: MonthViewProps): React.ReactElement {
  const {
    values,
    hasHeader,
    onValueClick,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasPrevPage,
    hasNextPage,
    onHeaderClick,
    disabledItemIndexes,
    activeItemIndex,
    currentHeadingValue,
    onCellHover,
    hoveredItemIndex,
    onMount,
    inline,
    localization,
    ...rest
  } = props as MonthViewProps & Record<DroppedViewProp, unknown>

  const setCalendarNode = useCalendarView(onMount)

  const headerProps: HeaderProps = {
    className: 'suicr-month-view-header',
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasPrevPage,
    hasNextPage,
    onHeaderClick,
    title: currentHeadingValue,
    displayWeeks: false,
    width: MONTH_CALENDAR_ROW_WIDTH,
    localization,
  }

  return React.createElement(
    Calendar,
    { ref: setCalendarNode, ...rest },
    hasHeader && React.createElement(Header, { ...headerProps }),
    React.createElement(Body, {
      width: MONTH_CALENDAR_ROW_WIDTH,
      data: values,
      onCellClick: onValueClick,
      onCellHover,
      active: activeItemIndex,
      hovered: hoveredItemIndex,
      disabled: disabledItemIndexes,
    }),
  )
}

export default MonthView
