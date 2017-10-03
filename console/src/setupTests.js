import raf from 'raf/polyfill';
import 'jest-enzyme';
import Enzyme, { shallow } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

Enzyme.configure({ adapter: new Adapter() });

const localStorageMock = {
    getItem: jest.fn(),
    setItem: jest.fn(),
    clear: jest.fn()
  };
global.localStorage = localStorageMock
  
global.requestAnimationFrame = function(callback) {
  setTimeout(callback, 0);
};