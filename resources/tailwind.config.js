module.exports = {
  darkMode: "class",
  content: [],
  safelist: ["dark"],
  theme: {
    extend: {},
    fontFamily: {
      sans: ["Fira Sans", "-apple-system", "BlinkMacSystemFont", "sans-serif"],
      serif: ["PT Serif", "serif"],
      mono: ["Fira Mono", "monospace"],
    },
  },
  variants: { extend: {} },
  plugins: [require("@tailwindcss/typography")],
};
