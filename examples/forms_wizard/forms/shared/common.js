function handleSubmit(ev) {
    var button = ev.target;
    var form = button.parentElement;
    form.classList.add("loading");
}

function init() {
    var source = document.getElementById("formTemplate").innerHTML;
    var template = Handlebars.compile(source);
    var html = template(data);

    document.getElementById("container").innerHTML = html;

    $('.ui.dropdown').dropdown();
}
