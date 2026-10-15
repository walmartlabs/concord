import _includes from 'lodash-es/includes'
import _range from 'lodash-es/range'
import { Moment } from 'moment'
import React from 'react'
import DayView, { WEEKS_TO_DISPLAY } from '../../views/DayView'
import { BasePickerOnChangeData, BasePickerProps, PickerBehavior, usePicker } from '../BasePicker'
import { buildDays, getDefaultEnabledDayPositions, getInitialDatePosition } from './sharedFunctions'

const PAGE_WIDTH = 7

export const DAYS_ON_PAGE = WEEKS_TO_DISPLAY * PAGE_WIDTH

export interface DayPickerOnChangeData extends BasePickerOnChangeData {
  value: {
    year: number
    month: number
    date: number
  }
}

type DayPickerProps = BasePickerProps

/*
  Return array of dates (strings) like ['31', '1', ...]
  that used to populate calendar's page.
*/
function buildCalendarValues(date: Moment): string[] {
  return buildDays(date, DAYS_ON_PAGE)
}

function getDisabledPositions(date: Moment): number[] {
  const enabled = getDefaultEnabledDayPositions(buildCalendarValues(date), date)

  return _range(0, DAYS_ON_PAGE).filter((position) => !_includes(enabled, position))
}

const DAY_PICKER_BEHAVIOR: PickerBehavior = {
  cellCount: DAYS_ON_PAGE,
  pageWidth: PAGE_WIDTH,

  buildCalendarValues,

  getDisabledPositions,

  getInitialDatePosition: (date, values, selectable) =>
    getInitialDatePosition(date.date().toString(), values, selectable),

  getActiveCellPosition: (date, props) => {
    /*
      Return position of a date that should be displayed as active
      (position in array returned by `buildCalendarValues`).
    */
    if (props.value && props.value.isSame(date, 'month')) {
      const disabledPositions = getDisabledPositions(date)
      const active = buildCalendarValues(date)
        .map((day, i) => (_includes(disabledPositions, i) ? undefined : day))
        .indexOf(props.value.date().toString())

      if (active >= 0) {
        return active
      }
    }
  },

  getChangeValue: (date, value) => ({
    year: date.year(),
    month: date.month(),
    date: parseInt(value, 10),
  }),

  switchPage: (date, direction) => {
    const switchedDate = date.clone()

    if (direction === 'next') {
      switchedDate.add(1, 'month')
    } else {
      switchedDate.subtract(1, 'month')
    }

    return switchedDate
  },

  /* Return currently selected year and month(string) to display in calendar header. */
  getHeadingValue: (date) => date.format('MMMM YYYY'),
}

function DayPicker(props: DayPickerProps): React.ReactElement {
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
  } = props

  const picker = usePicker(props, DAY_PICKER_BEHAVIOR)

  return React.createElement(DayView, {
    ...rest,
    values: picker.buildCalendarValues(),
    hasNextPage: picker.isNextPageAvailable(),
    hasPrevPage: picker.isPrevPageAvailable(),
    onNextPageBtnClick: picker.switchToNextPage,
    onPrevPageBtnClick: picker.switchToPrevPage,
    onValueClick: picker.handleChange,
    onBlur: picker.handleBlur,
    onMount: props.onCalendarViewMount,
    hoveredItemIndex: picker.state.hoveredCellPosition,
    onCellHover: picker.onHoveredCellPositionChange,
    currentHeadingValue: picker.getHeadingValue(),
    disabledItemIndexes: picker.getDisabledPositions(),
    activeItemIndex: picker.getActiveCellPosition(),
    localization,
  })
}

export default DayPicker
