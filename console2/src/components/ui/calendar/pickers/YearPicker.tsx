import _isNil from 'lodash-es/isNil'
import { Moment } from 'moment'
import React from 'react'
import YearView from '../views/YearView'
import { BasePickerOnChangeData, BasePickerProps, PickerBehavior, usePicker } from './BasePicker'

const PAGE_WIDTH = 3
const PAGE_HEIGHT = 4
const YEARS_ON_PAGE = PAGE_WIDTH * PAGE_HEIGHT

type YearPickerProps = BasePickerProps

export interface YearPickerOnChangeData extends BasePickerOnChangeData {
  value: {
    year: number
  }
}

/** Legacy calendar props accepted from untyped callers; they never reach the calendar. */
type DroppedYearPickerProp = 'disable' | 'enable' | 'inline' | 'maxDate' | 'minDate'

/*
  Return array of years (strings) like ['2012', '2013', ...]
  that used to populate calendar's page.
*/
function buildYears(date: Moment): string[] {
  const years: string[] = []
  const padd = date.year() % YEARS_ON_PAGE
  const firstYear = date.year() - padd

  for (let i = 0; i < YEARS_ON_PAGE; i++) {
    years[i] = (firstYear + i).toString()
  }

  return years
}

const YEAR_PICKER_BEHAVIOR: PickerBehavior = {
  cellCount: YEARS_ON_PAGE,
  pageWidth: PAGE_WIDTH,

  buildCalendarValues: buildYears,

  getDisabledPositions: () => [],

  getInitialDatePosition: (date, values, selectable) => {
    const currentYearIndex = values.indexOf(date.year().toString())

    if (selectable.indexOf(currentYearIndex) < 0) {
      return selectable[0]
    }

    return currentYearIndex
  },

  getActiveCellPosition: (date, props) => {
    /*
      Return position of a year that should be displayed as active
      (position in array returned by `buildCalendarValues`).
    */
    if (!_isNil(props.value)) {
      const yearIndex = buildYears(date).indexOf(props.value.year().toString())

      if (yearIndex >= 0) {
        return yearIndex
      }
    }
  },

  getChangeValue: (_date, value) => ({ year: parseInt(value, 10) }),

  switchPage: (date, direction) => {
    const switchedDate = date.clone()

    if (direction === 'next') {
      switchedDate.add(YEARS_ON_PAGE, 'year')
    } else {
      switchedDate.subtract(YEARS_ON_PAGE, 'year')
    }

    return switchedDate
  },
}

/*
  Note:
    use it like this <YearPicker key={someInputValue} />
    to make react create new instance when input value changes
*/
function YearPicker(props: YearPickerProps): React.ReactElement {
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
  } = props as YearPickerProps & Record<DroppedYearPickerProp, unknown>

  const picker = usePicker(props, YEAR_PICKER_BEHAVIOR)

  return React.createElement(YearView, {
    ...rest,
    values: picker.buildCalendarValues(),
    onNextPageBtnClick: picker.switchToNextPage,
    onPrevPageBtnClick: picker.switchToPrevPage,
    onValueClick: picker.handleChange,
    onBlur: picker.handleBlur,
    onMount: props.onCalendarViewMount,
    hoveredItemIndex: picker.state.hoveredCellPosition,
    onCellHover: picker.onHoveredCellPositionChange,
    hasPrevPage: picker.isPrevPageAvailable(),
    hasNextPage: picker.isNextPageAvailable(),
    disabledItemIndexes: picker.getDisabledPositions(),
    activeItemIndex: picker.getActiveCellPosition(),
    localization,
  })
}

export default YearPicker
