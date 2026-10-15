import _range from 'lodash-es/range'
import React from 'react'
import HourView from '../../views/HourView'
import {
  BasePickerOnChangeData,
  BasePickerProps,
  OptionalHeaderProps,
  PickerBehavior,
  usePicker,
} from '../BasePicker'
import { buildTimeStringWithSuffix, getCurrentDate } from './sharedFunctions'

const HOURS_ON_PAGE = 24
const PAGE_WIDTH = 4

type HourPickerProps = BasePickerProps & OptionalHeaderProps

export interface HourPickerOnChangeData extends BasePickerOnChangeData {
  value: {
    year: number
    month: number
    date: number
    hour: number
  }
}

/** Legacy calendar props accepted from untyped callers; they never reach the calendar. */
type DroppedHourPickerProp = 'disable' | 'inline' | 'maxDate' | 'minDate' | 'timeFormat'

const HOUR_PICKER_BEHAVIOR: PickerBehavior = {
  cellCount: HOURS_ON_PAGE,
  pageWidth: PAGE_WIDTH,

  /*
    Return array of hours (strings) like ['16:00', '17:00', ...]
    that used to populate calendar's page.
  */
  buildCalendarValues: () =>
    _range(0, 24)
      .map((h) => `${h < 10 ? '0' : ''}${h}`)
      .map((hour) => buildTimeStringWithSuffix(hour, '00')),

  getDisabledPositions: () => [],

  getInitialDatePosition: (date, _values, selectable) => {
    if (selectable.indexOf(date.hour()) < 0) {
      return selectable[0]
    }

    return date.hour()
  },

  getActiveCellPosition: (date, props) => {
    /*
      Return position of an hour that should be displayed as active
      (position in array returned by `buildCalendarValues`).
    */
    const { value } = props

    if (value && value.isSame(date, 'date')) {
      return value.hour()
    }
  },

  getChangeValue: (date, value, values) => ({
    year: date.year(),
    month: date.month(),
    date: date.date(),
    hour: values.indexOf(value),
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

function HourPicker(props: HourPickerProps): React.ReactElement {
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
  } = props as HourPickerProps & Record<DroppedHourPickerProp, unknown>

  const picker = usePicker(props, HOUR_PICKER_BEHAVIOR)

  return React.createElement(HourView, {
    ...rest,
    values: picker.buildCalendarValues(),
    onNextPageBtnClick: picker.switchToNextPage,
    onPrevPageBtnClick: picker.switchToPrevPage,
    hasPrevPage: picker.isPrevPageAvailable(),
    hasNextPage: picker.isNextPageAvailable(),
    onValueClick: picker.handleChange,
    onBlur: picker.handleBlur,
    onMount: props.onCalendarViewMount,
    hoveredItemIndex: picker.state.hoveredCellPosition,
    onCellHover: picker.onHoveredCellPositionChange,
    disabledItemIndexes: picker.getDisabledPositions(),
    activeItemIndex: picker.getActiveCellPosition(),
    currentHeadingValue: picker.getHeadingValue(),
    localization,
  })
}

export default HourPicker
