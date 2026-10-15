import _isNil from 'lodash-es/isNil'
import { Moment } from 'moment'
import React from 'react'
import MonthView from '../../views/MonthView'
import {
  BasePickerOnChangeData,
  BasePickerProps,
  OptionalHeaderProps,
  PickerBehavior,
  usePicker,
} from '../BasePicker'
import { MONTH_PAGE_WIDTH, MONTHS_IN_YEAR } from './const'
import { buildCalendarValues, getInitialDatePosition } from './sharedFunctions'

type MonthPickerProps = BasePickerProps & OptionalHeaderProps

export interface MonthPickerOnChangeData extends BasePickerOnChangeData {
  value: {
    year: number
    month: number
  }
}

/** Legacy calendar props accepted from untyped callers; they never reach the calendar. */
type DroppedMonthPickerProp = 'disable' | 'enable' | 'inline' | 'maxDate' | 'minDate'

const MONTH_PICKER_BEHAVIOR: PickerBehavior = {
  cellCount: MONTHS_IN_YEAR,
  pageWidth: MONTH_PAGE_WIDTH,

  buildCalendarValues: (_date: Moment, props) => buildCalendarValues(props.localization),

  getDisabledPositions: () => [],

  getInitialDatePosition: (date, _values, selectable) => getInitialDatePosition(selectable, date),

  getActiveCellPosition: (date, props) => {
    /*
      Return position of a month that should be displayed as active
      (position in array returned by `buildCalendarValues`).
    */
    if (!_isNil(props.value)) {
      if (props.value.year() === date.year()) {
        return props.value.month()
      }
    }
  },

  getChangeValue: (date, value, values) => ({
    year: parseInt(date.year().toString(), 10),
    month: values.indexOf(value),
  }),

  switchPage: (date, direction) => {
    const switchedDate = date.clone()

    if (direction === 'next') {
      switchedDate.add(1, 'year')
    } else {
      switchedDate.subtract(1, 'year')
    }

    return switchedDate
  },

  /* Return current year(string) to display in calendar header. */
  getHeadingValue: (date) => date.year().toString(),
}

/*
  Note:
    use it like this <MonthPicker key={someInputValue} />
    to make react create new instance when input value changes
*/
function MonthPicker(props: MonthPickerProps): React.ReactElement {
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
  } = props as MonthPickerProps & Record<DroppedMonthPickerProp, unknown>

  const picker = usePicker(props, MONTH_PICKER_BEHAVIOR)

  return React.createElement(MonthView, {
    ...rest,
    values: picker.buildCalendarValues(),
    onValueClick: picker.handleChange,
    onCellHover: picker.onHoveredCellPositionChange,
    onNextPageBtnClick: picker.switchToNextPage,
    onPrevPageBtnClick: picker.switchToPrevPage,
    hasPrevPage: picker.isPrevPageAvailable(),
    hasNextPage: picker.isNextPageAvailable(),
    onBlur: picker.handleBlur,
    onMount: props.onCalendarViewMount,
    disabledItemIndexes: picker.getDisabledPositions(),
    activeItemIndex: picker.getActiveCellPosition(),
    hoveredItemIndex: picker.state.hoveredCellPosition,
    currentHeadingValue: picker.getHeadingValue(),
    localization,
  })
}

export default MonthPicker
