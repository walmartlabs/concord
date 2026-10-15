export const htmlInputAttrs = [
  // REACT
  'selected',
  'defaultValue',
  'defaultChecked',

  // LIMITED HTML PROPS
  'accept',
  'autoCapitalize',
  'autoComplete',
  'autoCorrect',
  'autoFocus',
  'checked',
  'disabled',
  'form',
  'id',
  'inputMode',
  'lang',
  'list',
  'max',
  'maxLength',
  'min',
  'minLength',
  'multiple',
  'name',
  'pattern',
  'placeholder',
  'readOnly',
  'required',
  'step',
  'title',
  'type',
  'value',
]

export const htmlInputEvents = [
  // EVENTS
  // keyboard
  'onKeyDown',
  'onKeyPress',
  'onKeyUp',

  // focus
  'onFocus',
  'onBlur',

  // form
  'onChange',
  'onInput',

  // mouse
  'onClick',
  'onContextMenu',
  'onDrag',
  'onDragEnd',
  'onDragEnter',
  'onDragExit',
  'onDragLeave',
  'onDragOver',
  'onDragStart',
  'onDrop',
  'onMouseDown',
  'onMouseEnter',
  'onMouseLeave',
  'onMouseMove',
  'onMouseOut',
  'onMouseOver',
  'onMouseUp',

  // selection
  'onSelect',

  // touch
  'onTouchCancel',
  'onTouchEnd',
  'onTouchMove',
  'onTouchStart',
]

export const htmlInputProps = [...htmlInputAttrs, ...htmlInputEvents]

export const htmlImageProps = ['alt', 'height', 'src', 'srcSet', 'width', 'loading']

/**
 * Returns an array of objects consisting of: props of html input element and rest.
 */
export const partitionHTMLProps = <P extends object>(
  props: P,
  options: { htmlProps?: readonly string[]; includeAria?: boolean } = {},
): [Partial<P>, Partial<P>] => {
  const { htmlProps = htmlInputProps, includeAria = true } = options
  const inputProps: Record<string, unknown> = {}
  const rest: Record<string, unknown> = {}

  Object.keys(props).forEach((prop) => {
    const possibleAria = includeAria && (/^aria-.*$/.test(prop) || prop === 'role')
    const target = htmlProps.includes(prop) || possibleAria ? inputProps : rest
    target[prop] = props[prop as keyof P]
  })

  return [inputProps, rest] as unknown as [Partial<P>, Partial<P>]
}
