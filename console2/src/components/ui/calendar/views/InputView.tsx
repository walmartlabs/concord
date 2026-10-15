import _invoke from 'lodash-es/invoke'
import React, { useCallback, useEffect, useReducer, useRef } from 'react'
import { SemanticTRANSITIONS } from '@/components/ui/generic'
import Form from '@/components/ui/collections/Form/Form'
import Icon from '@/components/ui/elements/Icon/Icon'
import Popup from '@/components/ui/modules/Popup/Popup'
import Transition from '@/components/ui/modules/Transition/Transition'

const popupStyle = {
  padding: '0',
  filter: 'none',
}

interface InputViewProps
  extends Omit<
    React.InputHTMLAttributes<HTMLInputElement>,
    'value' | 'onChange' | 'tabIndex'
  > {
  /** Current value of the input field. */
  value?: string

  /** Used for passing input dom node to parent component. */
  onMount: (e: HTMLElement) => void

  /** Called after input field value has changed. */
  onChange: (e: React.SyntheticEvent<HTMLElement>, data: { value: string }) => void

  /** Called when component loses focus. */
  onBlur?: (e: React.SyntheticEvent<HTMLElement>) => void

  /** Forse popup to close. */
  closePopup: () => void

  /** Forse popup to open. */
  openPopup: () => void

  /** Called on input focus. */
  onFocus?: React.FocusEventHandler<HTMLInputElement>

  /** Function for rendering picker. */
  renderPicker: () => React.ReactNode

  /** Whether to show the clear affordance. */
  clearable?: boolean

  /** Icon position. Default: 'right'. */
  iconPosition?: 'left' | 'right'

  /** Whether popup is closed. */
  popupIsClosed?: boolean

  /** Duration of the CSS transition animation in milliseconds. */
  duration?: number

  /** Named animation event to use. Must be defined in CSS. */
  animation?: SemanticTRANSITIONS

  /** Where to display popup. */
  popupPosition?:
    | 'top left'
    | 'top right'
    | 'bottom right'
    | 'bottom left'
    | 'right center'
    | 'left center'
    | 'top center'
    | 'bottom center'

  /** Provided by `InputView`, it keeps the native `tabIndex` string form. */
  tabIndex?: number | string
}

/** Legacy calendar props accepted from untyped callers; they never reach the DOM. */
type DroppedInputViewProp =
  | 'clearIcon'
  | 'closeOnMouseLeave'
  | 'hideMobileKeyboard'
  | 'icon'
  | 'inline'
  | 'inlineLabel'
  | 'mountNode'
  | 'onClear'
  | 'pickerStyle'
  | 'pickerWidth'

/** Props of the internal `Form.Input` adapter: `InputView` props without the popup plumbing. */
type FormInputWithRefProps = Omit<
  InputViewProps,
  | 'animation'
  | 'closePopup'
  | 'duration'
  | 'iconPosition'
  | 'onMount'
  | 'openPopup'
  | 'popupIsClosed'
  | 'popupPosition'
  | 'renderPicker'
> & {
  /** Icon position forwarded to `Input`. */
  iconPosition?: 'left'

  /** Ref to the native `<input />` element. */
  innerRef: React.Ref<HTMLInputElement>

  /** Called after click on the clear affordance. */
  onClear?: (e: React.MouseEvent<HTMLElement>) => void
}

function FormInputWithRef(props: FormInputWithRefProps): React.ReactElement {
  const {
    value,
    clearable,
    onClear,
    innerRef,
    onFocus,
    onBlur,
    onMouseEnter,
    ...rest
  } = props

  const ClearIcon = React.createElement(Icon, { name: 'remove', link: true, onClick: onClear })

  return React.createElement(
    Form.Input,
    { onFocus, onBlur, onClick: onFocus as unknown as React.MouseEventHandler<HTMLInputElement>, onMouseEnter, icon: true, ...rest },
    value && clearable ? ClearIcon : React.createElement(Icon, { name: 'calendar' }),
    React.createElement('input', { ref: innerRef, value }),
  )
}

function InputView(props: InputViewProps): React.ReactElement {
  const inputNode = useRef<HTMLInputElement>()
  const popupNode = useRef<HTMLDivElement>()
  const mouseLeaveTimeout = useRef<number | null>()

  const propsRef = useRef(props)
  propsRef.current = props

  const [, forceUpdate] = useReducer((count: number) => count + 1, 0)

  const scrollListener = useCallback((): void => {
    propsRef.current.closePopup()
  }, [])

  const setScrollListener = useCallback((): void => {
    window.addEventListener('scroll', scrollListener)
  }, [scrollListener])

  const unsetScrollListener = useCallback((): void => {
    window.removeEventListener('scroll', scrollListener)
  }, [scrollListener])

  useEffect(
    () => () => {
      unsetScrollListener()
      window.clearTimeout(mouseLeaveTimeout.current)
    },
    [unsetScrollListener],
  )

  const setInputNode = useCallback((node: HTMLInputElement | null): void => {
    inputNode.current = node
    propsRef.current.onMount(node)
  }, [])

  const setPopupNode = useCallback((node: HTMLDivElement | null): void => {
    popupNode.current = node
  }, [])

  const onBlur = (e: React.FocusEvent<HTMLElement>, ...args: unknown[]): void => {
    const { closePopup } = props

    if (e.relatedTarget !== popupNode.current && e.relatedTarget !== inputNode.current) {
      _invoke(props, 'onBlur', e, ...args)
      closePopup()
    }
  }

  const onMouseLeave = (e: React.MouseEvent<HTMLElement>, ...args: unknown[]): void => {
    const { closePopup } = props

    if (e.relatedTarget !== popupNode.current && e.relatedTarget !== inputNode.current) {
      _invoke(props, 'onMouseLeave', e, ...args)
      mouseLeaveTimeout.current = window.setTimeout(() => {
        if (mouseLeaveTimeout.current) {
          closePopup()
        }
      }, 500)
    }
  }

  const onMouseEnter = (e: React.MouseEvent<HTMLElement>, ...args: unknown[]): void => {
    _invoke(props, 'onMouseEnter', e, ...args)

    if (e.currentTarget === popupNode.current || e.currentTarget === inputNode.current) {
      clearTimeout(mouseLeaveTimeout.current)
      mouseLeaveTimeout.current = null
    }
  }

  const {
    renderPicker,
    popupPosition,
    value,
    onChange,
    popupIsClosed,
    tabIndex,
    onMount,
    closePopup,
    openPopup,
    animation,
    duration,
    iconPosition,
    readOnly,
    inline,
    closeOnMouseLeave,
    onClear,
    children,
    inlineLabel,
    mountNode,
    pickerWidth,
    pickerStyle,
    icon,
    hideMobileKeyboard,
    clearIcon,
    clearable,
    ...rest
  } = props as InputViewProps & Record<DroppedInputViewProp, unknown>

  const inputElement = React.createElement(FormInputWithRef, {
    ...rest,
    readOnly,
    iconPosition: iconPosition !== 'right' ? iconPosition : undefined,
    innerRef: setInputNode,
    value,
    tabIndex,
    clearable: props.clearable,
    onClear: (e) => onChange(e, { ...rest, value: '' }),
    onFocus: (e) => {
      _invoke(props, 'onFocus', e, props)
      openPopup()
    },
    onBlur,
    onMouseEnter,
    onChange,
  })

  return React.createElement(
    React.Fragment,
    null,
    inputElement,
    !readOnly &&
      React.createElement(
        Transition,
        {
          unmountOnHide: true,
          mountOnShow: true,
          visible: !popupIsClosed,
          animation,
          duration,
          onComplete: () => {
            if (popupIsClosed) {
              unsetScrollListener()
              // TODO: for some reason sometimes transition component
              // doesn't hide even though `popupIsClosed === true`
              // To hide it we need to rerender component
              forceUpdate()
            } else {
              setScrollListener()
            }
          },
        },
        React.createElement(
          Popup,
          {
            position: popupPosition,
            open: true,
            hoverable: true,
            flowing: true,
            style: popupStyle,
            context: inputNode.current,
            on: 'hover',
          },
          React.createElement(
            'div',
            {
              onBlur,
              onMouseLeave,
              onMouseEnter,
              style: { outline: 'none' },
              tabIndex: 0,
              ref: setPopupNode,
            },
            renderPicker(),
          ),
        ),
      ),
  )
}

InputView.defaultProps = {
  tabIndex: '0',
  clearable: false,
  animation: 'scale',
  duration: 200,
  iconPosition: 'right',
} satisfies Partial<InputViewProps>

export default InputView
