import React from 'react'
import {
  BaseCalendarViewProps,
  CalendarWithHeaderViewProps,
  HeadingValueProps,
  SingleSelectionCalendarViewProps,
  useCalendarView,
} from './BaseCalendarView'
import Calendar from './Calendar'
import Body from './CalendarBody/Body'
import Header from './CalendarHeader/Header'

export const DAY_CALENDAR_ROW_WIDTH = 7
export const WEEKS_TO_DISPLAY = 6

type DayViewProps = BaseCalendarViewProps &
  HeadingValueProps &
  SingleSelectionCalendarViewProps &
  CalendarWithHeaderViewProps

function DayView(props: DayViewProps): React.ReactElement {
  const {
    values,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    onValueClick,
    hasNextPage,
    hasPrevPage,
    currentHeadingValue,
    onHeaderClick,
    disabledItemIndexes,
    activeItemIndex,
    hoveredItemIndex,
    onCellHover,
    onMount,
    localization,
    ...rest
  } = props

  const setCalendarNode = useCalendarView(onMount)

  return React.createElement(
    Calendar,
    { ref: setCalendarNode, ...rest },
    React.createElement(Header, {
      className: 'suicr-day-view-header',
      width: DAY_CALENDAR_ROW_WIDTH,
      displayWeeks: true,
      onNextPageBtnClick,
      onPrevPageBtnClick,
      hasNextPage,
      hasPrevPage,
      title: currentHeadingValue,
      onHeaderClick,
      localization,
    }),
    React.createElement(Body, {
      width: DAY_CALENDAR_ROW_WIDTH,
      data: values,
      hovered: hoveredItemIndex,
      onCellHover,
      onCellClick: onValueClick,
      active: activeItemIndex,
      disabled: disabledItemIndexes,
    }),
  )
}

export default DayView
