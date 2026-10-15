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

const MINUTE_CALENDAR_ROW_WIDTH = 3

type MinuteViewProps = BaseCalendarViewProps &
  SingleSelectionCalendarViewProps &
  CalendarWithOptionalHeaderViewProps

/** Legacy calendar prop accepted from untyped callers; it never reaches the table. */
type DroppedViewProp = 'inline'

function MinuteView(props: MinuteViewProps): React.ReactElement {
  const {
    values,
    hasHeader,
    onValueClick,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasNextPage,
    hasPrevPage,
    onHeaderClick,
    activeItemIndex,
    currentHeadingValue,
    hoveredItemIndex,
    disabledItemIndexes,
    onCellHover,
    onMount,
    inline,
    localization,
    ...rest
  } = props as MinuteViewProps & Record<DroppedViewProp, unknown>

  const setCalendarNode = useCalendarView(onMount)

  const headerProps: HeaderProps = {
    className: 'suicr-minute-view-header',
    onHeaderClick,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasNextPage,
    hasPrevPage,
    title: currentHeadingValue,
    width: MINUTE_CALENDAR_ROW_WIDTH,
    displayWeeks: false,
    localization,
  }

  return React.createElement(
    Calendar,
    { ref: setCalendarNode, ...rest },
    hasHeader && React.createElement(Header, { ...headerProps }),
    React.createElement(Body, {
      width: MINUTE_CALENDAR_ROW_WIDTH,
      data: values,
      hovered: hoveredItemIndex,
      onCellHover,
      onCellClick: onValueClick,
      active: activeItemIndex,
      disabled: disabledItemIndexes,
    }),
  )
}

export default MinuteView
