var document = { URL: "whatever",
  write: function Document_prototype_write(x) { } };
var id = function _id(x) { return x; };
function Id() { this.id = id; }
function SubId() { }; SubId.prototype = new Id();

if (Math.random.call(null) > 0) {
    var id1 = new Id();
    var text = id1.id.call(document, document.URL);
} else if (Math.random.call(null) > 0) {
    var id2 = new SubId();
    var text = id2.id("not a url");
} else {
    var id3 = new SubId();
    var text = id3.id(document.URL);
}
document.write(text);
