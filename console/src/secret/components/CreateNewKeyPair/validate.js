const validate = values => {
    const errors = {};
    if (!values.concordId) {
      errors.concordId = 'Required';
    }
    return errors;
  };
  
  export default validate;
  