import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import moment, { Moment } from 'moment'

import React, { useCallback, useEffect, useRef, useState } from 'react'
import { SemanticTRANSITIONS } from '@/components/ui/generic'
import tick from '../lib/tick'
import { BasePickerOnChangeData, BasePickerProps } from '../pickers/BasePicker'
import { buildValue, parseValue } from './parse'

export interface BaseInputProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'> {
  /** Label rendered by the retained Form.Input adapter. */
  label?: React.ReactNode

  /** Currently selected value. */
  value: string

  /** Called on selected value change. */
  onChange: (e: React.SyntheticEvent<HTMLElement>, data: { name?: string; value: string }) => void

  /** If true, popup closes after selecting a value. */
  closable?: boolean

  /** Icon position inside Input. Default: 'right'. */
  iconPosition?: 'right' | 'left'

  /** Whether the selection can be cleared. */
  clearable?: boolean

  /** Position for the popup. */
  popupPosition?:
    | 'top left'
    | 'top right'
    | 'bottom left'
    | 'bottom right'
    | 'right center'
    | 'left center'
    | 'top center'
    | 'bottom center'

  /** Duration of the CSS transition animation in milliseconds. */
  duration?: number

  /** Named animation event to use. Must be defined in CSS. */
  animation?: SemanticTRANSITIONS

  /** Moment date localization */
  localization?: string
}





export interface BaseInputState {
  popupIsClosed: boolean
}

/** Default props of the concrete inputs; the former `BaseInput.defaultProps`. */
export const BaseInputDefaultProps = {
  localization: moment.locale(),
} satisfies Partial<BaseInputProps>

/** Popup, focus and mount bookkeeping shared by the concrete calendar inputs. */
export interface BaseInputController {
  /** Whether the popup with a picker is closed. */
  popupIsClosed: boolean

  /** Closes the popup and notifies the parent component through `onBlur`. */
  closePopup: () => void

  /** Opens the popup with a picker. */
  openPopup: () => void

  /** Whether the calendar itself is focused. */
  isPickerInFocus: () => boolean

  /** Whether the input that triggers the popup is focused. */
  isTriggerInFocus: () => boolean

  /** Stores the DOM node of a mounted calendar. */
  onCalendarViewMount: (calendarNode: HTMLElement) => void

  /** Stores the DOM node of the mounted input. */
  onInputViewMount: (inputNode: HTMLElement) => void
}

/** Replaces the abstract `BaseInput` class for the concrete inputs. */
export function useBaseInput(props: BaseInputProps): BaseInputController {
  const [state, setState] = useState<BaseInputState>({ popupIsClosed: true })

  const propsRef = useRef(props)
  propsRef.current = props

  const calendarNode = useRef<HTMLElement>()
  const inputNode = useRef<HTMLElement>()

  const closePopup = useCallback((): void => {
    _invoke(propsRef.current, 'onBlur')
    setState({ popupIsClosed: true })
  }, [])

  const openPopup = useCallback((): void => {
    setState({ popupIsClosed: false })
  }, [])

  const isPickerInFocus = useCallback((): boolean => document.activeElement === calendarNode.current, [])

  const isTriggerInFocus = useCallback((): boolean => document.activeElement === inputNode.current, [])

  const onCalendarViewMount = useCallback((calendarViewNode: HTMLElement): void => {
    calendarNode.current = calendarViewNode
  }, [])

  const onInputViewMount = useCallback((inputViewNode: HTMLElement): void => {
    inputNode.current = inputViewNode
  }, [])

  return {
    popupIsClosed: state.popupIsClosed,
    closePopup,
    openPopup,
    isPickerInFocus,
    isTriggerInFocus,
    onCalendarViewMount,
    onInputViewMount,
  }
}

/** Date fields a concrete input keeps to feed its pickers. */
export interface DateInputValues {
  year?: number
  month?: number
  date?: number
  hour?: number
  minute?: number
}

/** Props the date fields are parsed from. */
export type DateInputFormatProps = BaseInputProps & {
  dateFormat?: string
  dateTimeFormat?: string
}

/** Customizes the shared date input behavior for a concrete input. */
export interface DateInputConfig<M extends string> {
  /** Resolves the moment format used to parse and to render the input value. */
  getFormat: (props: DateInputFormatProps) => string

  /** Maps a parsed value to the internal date fields. */
  getDateValues: (value: Moment) => DateInputValues

  /** Mode that follows the given calendar mode. */
  getNextMode: (mode: M) => M

  /** Mode that precedes the given calendar mode. */
  getPrevMode: (mode: M) => M

  /** Mode in which a selection is committed through `onChange`. */
  commitMode: M

  /** Whether selections are dispatched on the next tick, see `tick`. */
  delayedSelection?: boolean

  /** Renders the picker that belongs to the given mode. */
  renderPicker: (mode: M, pickerProps: BasePickerProps) => React.ReactElement
}

/** Shared behavior of the concrete date inputs. */
export interface DateInputController extends BaseInputController {
  /** Renders the picker of the currently displayed calendar mode. */
  renderPicker: () => React.ReactElement

  /** Parses a manually edited input value and forwards it through `onChange`. */
  handleInputValueChange: (e: React.SyntheticEvent<HTMLElement>, data: { value: string }) => void
}

/** Shared behavior of the concrete date inputs, replaces the abstract `BaseInput` subclass state. */
export function useDateInput<M extends string>(
  props: DateInputFormatProps,
  config: DateInputConfig<M>,
): DateInputController {
  const baseInput = useBaseInput(props)

  const [mode, setMode] = useState<M>(() => 'day' as M)
  const [dateValues, setDateValues] = useState<DateInputValues>(() => {
    const parsedValue = parseValue(
      props.value,
      config.getFormat(props),
      props.localization!,
    )

    return parsedValue ? config.getDateValues(parsedValue) : {}
  })

  const propsRef = useRef(props)
  propsRef.current = props

  const configRef = useRef(config)
  configRef.current = config

  const dateValuesRef = useRef(dateValues)
  dateValuesRef.current = dateValues

  const modeRef = useRef(mode)
  modeRef.current = mode

  // Update internal date if ``value`` prop changed and was successfully parsed.
  const previousValue = useRef(props.value)
  useEffect(() => {
    if (previousValue.current === props.value) {
      return
    }
    previousValue.current = props.value

    const parsed = parseValue(
      props.value,
      configRef.current.getFormat(props),
      props.localization!,
    )

    if (parsed) {
      setDateValues(configRef.current.getDateValues(parsed))
    }
  }, [props.value])

  /*
    Creates a moment instance from the values stored in the internal state
    (year, month, date, hour, minute) in order to pass this moment instance to
    the underlying picker.
    Returns undefined if none of these fields has a value.
  */
  const parseInternalValue = (): Moment | undefined => {
    const { year, month, date, hour, minute } = dateValuesRef.current

    if (!_isNil(year) || !_isNil(month) || !_isNil(date) || !_isNil(hour) || !_isNil(minute)) {
      return moment({ year, month, date, hour, minute })
    }
  }

  const switchToNextModeUndelayed = useCallback((): void => {
    setMode((current) => configRef.current.getNextMode(current))
  }, [])

  const switchToNextMode = useCallback((): void => {
    tick(switchToNextModeUndelayed)
  }, [switchToNextModeUndelayed])

  const switchToPrevModeUndelayed = useCallback((): void => {
    setMode((current) => configRef.current.getPrevMode(current))
  }, [])

  const switchToPrevMode = useCallback((): void => {
    tick(switchToPrevModeUndelayed)
  }, [switchToPrevModeUndelayed])

  const handleSelectUndelayed = (
    e: React.SyntheticEvent<HTMLElement>,
    { value }: BasePickerOnChangeData,
  ): void => {
    const currentProps = propsRef.current
    const { commitMode, getDateValues, getFormat } = configRef.current
    const commitsValue = modeRef.current === commitMode

    if (commitsValue && currentProps.closable) {
      baseInput.closePopup()
    }

    if (commitsValue) {
      const outValue = moment(value).format(getFormat(currentProps))

      _invoke(currentProps, 'onChange', e, { ...currentProps, value: outValue })
    }

    setDateValues({
      year: value.year,
      month: value.month,
      date: value.date,
      hour: value.hour,
      minute: value.minute,
    })

    if (!commitsValue) {
      switchToNextMode()
    }
  }

  const handleSelect = (
    e: React.SyntheticEvent<HTMLElement>,
    data: BasePickerOnChangeData,
  ): void => {
    if (configRef.current.delayedSelection) {
      tick(handleSelectUndelayed, e, data)
    } else {
      handleSelectUndelayed(e, data)
    }
  }

  const renderPicker = (): React.ReactElement => {
    const currentProps = propsRef.current
    const localization = currentProps.localization!
    const format = configRef.current.getFormat(currentProps)

    const pickerProps: BasePickerProps = {
      tabIndex: currentProps.tabIndex,
      isPickerInFocus: baseInput.isPickerInFocus,
      isTriggerInFocus: baseInput.isTriggerInFocus,
      onCalendarViewMount: baseInput.onCalendarViewMount,
      closePopup: baseInput.closePopup,
      onChange: handleSelect,
      onHeaderClick: switchToPrevMode,
      initializeWith: buildValue(parseInternalValue(), localization, format),
      value: buildValue(currentProps.value, localization, format, null),
      localization,
    }

    return configRef.current.renderPicker(mode, pickerProps)
  }

  /** Keeps internal state in sync with input field value. */
  const handleInputValueChange = (
    e: React.SyntheticEvent<HTMLElement>,
    { value }: { value: string },
  ): void => {
    const currentProps = propsRef.current
    const parsedValue = moment(value, configRef.current.getFormat(currentProps), currentProps.localization!)

    if (parsedValue.isValid()) {
      setDateValues(configRef.current.getDateValues(parsedValue))
    }

    _invoke(currentProps, 'onChange', e, { ...currentProps, value })
  }

  return {
    ...baseInput,
    renderPicker,
    handleInputValueChange,
  }
}
