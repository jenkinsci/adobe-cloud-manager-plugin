(function () {
  window.observedCmSelectElements = window.observedCmSelectElements || [];
  function isObserved(el) {
    return window.observedCmSelectElements.some(function (cmEl) {
      return cmEl === el;
    })
  }

  var forEach = function (elList, cb) {
    Array.prototype.forEach.call(elList, cb)
  }

  function toggleLoading(target) {
    if (target.classList.contains("select-ajax-pending")) {
      target.parentElement.classList.add("cm-loading");
    } else {
      target.parentElement.classList.remove("cm-loading");
    }
  }

  // setup observer
  var mutationObserver = new MutationObserver(function callback(mutationsList, observer) {
    mutationsList.forEach(mutation => {
      if (mutation.attributeName === 'class') {
        toggleLoading(mutation.target)
      }
    })
  })

  // observe
  setTimeout(function () {
    var selects = document.querySelectorAll(".cm-select");
    forEach(selects, function (select) {
      if (!isObserved(select)) {
        window.observedCmSelectElements.push(select)
        toggleLoading(select)
        mutationObserver.observe(select, { attributes: true })
      }
    });
  }, 0)
})();