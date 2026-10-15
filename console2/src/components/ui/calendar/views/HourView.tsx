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

const HOUR_CALENDAR_ROW_WIDTH = 4

type HourViewProps = BaseCalendarViewProps &
  SingleSelectionCalendarViewProps &
  CalendarWithOptionalHeaderViewProps

/** Legacy calendar prop accepted from untyped callers; it never reaches the table. */
type DroppedViewProp = 'inline'

function HourView(props: HourViewProps): React.ReactElement {
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
    hoveredItemIndex,
    onCellHover,
    onMount,
    inline,
    localization,
    ...rest
  } = props as HourViewProps & Record<DroppedViewProp, unknown>

  const setCalendarNode = useCalendarView(onMount)

  const headerProps: HeaderProps = {
    className: 'suicr-hour-view-header',
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasPrevPage,
    hasNextPage,
    onHeaderClick,
    title: currentHeadingValue,
    width: HOUR_CALENDAR_ROW_WIDTH,
    displayWeeks: false,
    localization,
  }

  return React.createElement(
    Calendar,
    { ref: setCalendarNode, ...rest },
    hasHeader && React.createElement(Header, { ...headerProps }),
    React.createElement(Body, {
      data: values,
      width: HOUR_CALENDAR_ROW_WIDTH,
      onCellClick: onValueClick,
      hovered: hoveredItemIndex,
      onCellHover,
      active: activeItemIndex,
      disabled: disabledItemIndexes,
    }),
  )
}

export default HourView
