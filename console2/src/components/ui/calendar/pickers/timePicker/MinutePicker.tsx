import _range from 'lodash-es/range'
import React from 'react'
import MinuteView from '../../views/MinuteView'
import {
  BasePickerOnChangeData,
  BasePickerProps,
  OptionalHeaderProps,
  PickerBehavior,
  usePicker,
} from '../BasePicker'
import { buildTimeStringWithSuffix, getCurrentDate } from './sharedFunctions'

const MINUTES_STEP = 5
const MINUTES_ON_PAGE = 12
const PAGE_WIDTH = 3

type MinutePickerProps = BasePickerProps & OptionalHeaderProps

export interface MinutePickerOnChangeData extends BasePickerOnChangeData {
  value: {
    year: number
    month: number
    date: number
    hour: number
    minute: number
  }
}

/** Legacy calendar props accepted from untyped callers; they never reach the calendar. */
type DroppedMinutePickerProp = 'disable' | 'inline' | 'maxDate' | 'minDate' | 'timeFormat'

function getMinuteCellPosition(minute: number): number {
  return Math.floor(minute / MINUTES_STEP)
}

const MINUTE_PICKER_BEHAVIOR: PickerBehavior = {
  cellCount: MINUTES_ON_PAGE,
  pageWidth: PAGE_WIDTH,

  /*
    Return array of minutes (strings) like ['16:15', '16:20', ...]
    that used to populate calendar's page.
  */
  buildCalendarValues: (date) => {
    const hour = date.hour() < 10 ? `0${date.hour().toString()}` : date.hour().toString()

    return _range(0, 60, MINUTES_STEP)
      .map((minute) => `${minute < 10 ? '0' : ''}${minute}`)
      .map((minute) => buildTimeStringWithSuffix(hour, minute))
  },

  getDisabledPositions: () => [],

  getInitialDatePosition: (date, _values, selectable) => {
    const currentPosition = getMinuteCellPosition(date.minute())

    if (selectable.indexOf(currentPosition) < 0) {
      return selectable[0]
    }

    return currentPosition
  },

  getActiveCellPosition: (date, props) => {
    const { value } = props

    if (value && value.isSame(date, 'date')) {
      return getMinuteCellPosition(value.minutes())
    }
  },

  getChangeValue: (date, value, values) => ({
    year: date.year(),
    month: date.month(),
    date: date.date(),
    hour: date.hour(),
    minute: values.indexOf(value) * MINUTES_STEP,
  }),

  switchPage: (date, direction) => {
    const switchedDate = date.clone()

    if (direction === 'next') {
      switchedDate.add(1, 'day')
    } else {
      switchedDate.subtract(1, 'day')
    }

    return switchedDate
  },

  /* Return currently selected month, date and year(string) to display in calendar header. */
  getHeadingValue: (date) => getCurrentDate(date),
}

function MinutePicker(props: MinutePickerProps): React.ReactElement {
  const {
    onChange,
    value,
    initializeWith,
    closePopup,
    isPickerInFocus,
    isTriggerInFocus,
    onCalendarViewMount,
    localization,
    ...rest
  } = props as MinutePickerProps & Record<DroppedMinutePickerProp, unknown>

  const picker = usePicker(props, MINUTE_PICKER_BEHAVIOR)

  return React.createElement(MinuteView, {
    ...rest,
    values: picker.buildCalendarValues(),
    onNextPageBtnClick: picker.switchToNextPage,
    onPrevPageBtnClick: picker.switchToPrevPage,
    onValueClick: picker.handleChange,
    hoveredItemIndex: picker.state.hoveredCellPosition,
    onCellHover: picker.onHoveredCellPositionChange,
    onBlur: picker.handleBlur,
    onMount: props.onCalendarViewMount,
    hasNextPage: picker.isNextPageAvailable(),
    hasPrevPage: picker.isPrevPageAvailable(),
    disabledItemIndexes: picker.getDisabledPositions(),
    currentHeadingValue: picker.getHeadingValue(),
    activeItemIndex: picker.getActiveCellPosition(),
    localization,
  })
}

export default MinutePicker
