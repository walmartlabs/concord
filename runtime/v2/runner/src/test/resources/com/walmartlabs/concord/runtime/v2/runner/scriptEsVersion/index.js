// dummy script to exercise newer ECMAScript features
const vars = context.variables();
const phrase = vars.get("phrase");

// lambda expressions
const varsByKey = [...context.variables().toMap().entrySet()].reduce(
  (acc, it) =>
    // splat operator
    ({ ...acc, [it[0]]: it[1] }),
  {}
);

// sets, string interpolation
const uniqueKeys = new Set(Object.keys(varsByKey));
console.log(`uniqueKeys size: ${uniqueKeys.size}`)

// array methods, string methods
const charCountProduct = Object.values(
  `${phrase}`
    .split("")
    .flatMap((char) => char.at(-1))
    .reduce((acc, char) => {
      const count = char in acc ? acc[char] : 0;
      return { ...acc, [char]: count + 1 };
    }, {})
).reduce((acc, n) => acc * n, 1);

// destructuring, JSON methods
vars.set("json", JSON.stringify({ charCountProduct, varsByKey }));
