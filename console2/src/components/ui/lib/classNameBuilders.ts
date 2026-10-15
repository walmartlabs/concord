import { numberToWord } from './numberToWord'

/*
 * There are 3 prop patterns used to build up the className for a component.
 * Each utility here is meant for use in a classnames() argument.
 *
 * There is no util for valueOnly() because it would simply return val.
 * Use the prop value inline instead.
 *   <Label size='big' />
 *   <div class="ui big label"></div>
 */

/**
 * Props where only the prop key is used in the className.
 */
export const useKeyOnly = (val: boolean | undefined, key: string): string | false =>
  !!val && key

/**
 * Props that require both a key and value to create a className.
 */
export const useValueAndKey = (
  val: string | boolean | undefined,
  key: string,
): string | false => !!val && val !== true && `${val} ${key}`

/**
 * Props whose key will be used in className, or value and key.
 */
export const useKeyOrValueAndKey = (
  val: string | boolean | undefined,
  key: string,
): string | false => !!val && (val === true ? key : `${val} ${key}`)

//
// Prop to className exceptions
//

/**
 * The "textAlign" prop follows the useValueAndKey except when the value is "justified'.
 * In this case, only the class "justified" is used, ignoring the "aligned" class.
 */
export const useTextAlignProp = (val: string | boolean): string | false =>
  val === 'justified' ? 'justified' : useValueAndKey(val, 'aligned')

/**
 * The "verticalAlign" prop follows the useValueAndKey.
 */
export const useVerticalAlignProp = (val: string | boolean): string | false =>
  useValueAndKey(val, 'aligned')

/**
 * Create "X", "X wide" and "equal width" classNames.
 * "X" is a numberToWord value and "wide" is configurable.
 */
export const useWidthProp = (
  val: string | number,
  widthClass: string = '',
  canEqual: boolean = false,
): string | number => {
  if (canEqual && val === 'equal') {
    return 'equal width'
  }

  const valType = typeof val

  if ((valType === 'string' || valType === 'number') && widthClass) {
    return `${numberToWord(val)} ${widthClass}`
  }

  return numberToWord(val)
}
