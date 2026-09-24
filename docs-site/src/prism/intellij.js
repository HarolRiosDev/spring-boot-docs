// Temas de resaltado inspirados en IntelliJ IDEA: "IntelliJ Light" (modo claro) y "Darcula" (modo oscuro).
//
// `punctuation` no se define a propósito: Prism marca las anotaciones de Java como
// `annotation punctuation`, y prism-react-renderer aplica los estilos de cada tipo en orden,
// así que un color para `punctuation` taparía el de `annotation`. Sin él, llaves, paréntesis y
// puntos toman el color `plain`, que es además lo que hace IntelliJ.

/** @type {import('prism-react-renderer').PrismTheme} */
const intellijLight = {
  plain: {
    color: '#080808',
    backgroundColor: '#F7F8FA',
  },
  styles: [
    {types: ['comment', 'prolog', 'doctype', 'cdata'], style: {color: '#8C8C8C', fontStyle: 'italic'}},
    {types: ['keyword', 'boolean', 'builtin'], style: {color: '#0033B3'}},
    {types: ['annotation'], style: {color: '#9E880D'}},
    {types: ['string', 'char', 'attr-value', 'regex'], style: {color: '#067D17'}},
    {types: ['number'], style: {color: '#1750EB'}},
    {types: ['function'], style: {color: '#00627A'}},
    {types: ['constant', 'property', 'variable'], style: {color: '#871094'}},
    {types: ['atrule', 'attr-name', 'tag', 'selector'], style: {color: '#0033B3'}},
    {types: ['deleted'], style: {color: '#C7222D'}},
    {types: ['inserted'], style: {color: '#067D17'}},
  ],
};

/** @type {import('prism-react-renderer').PrismTheme} */
const intellijDark = {
  plain: {
    color: '#A9B7C6',
    backgroundColor: '#2B2B2B',
  },
  styles: [
    {types: ['comment', 'prolog', 'doctype', 'cdata'], style: {color: '#808080', fontStyle: 'italic'}},
    {types: ['keyword', 'boolean', 'builtin'], style: {color: '#CC7832'}},
    {types: ['annotation'], style: {color: '#BBB529'}},
    {types: ['string', 'char', 'attr-value', 'regex'], style: {color: '#6A8759'}},
    {types: ['number'], style: {color: '#6897BB'}},
    {types: ['function'], style: {color: '#FFC66D'}},
    {types: ['constant', 'property', 'variable'], style: {color: '#9876AA'}},
    {types: ['atrule', 'attr-name', 'tag', 'selector'], style: {color: '#CC7832'}},
    {types: ['deleted'], style: {color: '#FF6B68'}},
    {types: ['inserted'], style: {color: '#6A8759'}},
  ],
};

module.exports = {intellijLight, intellijDark};
