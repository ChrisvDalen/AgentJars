/*
 * Client-side search for the static build.
 *
 * The live registry filters the listing on the server. A static host cannot, so the listing page
 * is prerendered with every agent and filtered here instead. Both render the same markup; only
 * who does the filtering changes.
 */
(function () {
  'use strict';

  // Present only when the listing was prerendered with every agent; the live registry filters
  // server-side and leaves this off, so the script stays inert there.
  const listing = document.querySelector('[data-agent-listing][data-client-search]');
  if (!listing) {
    return;
  }

  const cards = Array.from(listing.querySelectorAll('[data-agent]'));
  const form = document.querySelector('[data-search-form]');
  const input = form ? form.querySelector('input[name="q"]') : null;
  const count = document.querySelector('[data-result-count]');
  const empty = document.querySelector('[data-empty-state]');
  const tagLinks = Array.from(document.querySelectorAll('[data-tag-filter]'));

  function matches(card, query, tag) {
    if (tag && !(card.dataset.tags || '').split(' ').includes(tag)) {
      return false;
    }
    return !query || (card.dataset.search || '').includes(query);
  }

  function apply(query, tag) {
    const needle = (query || '').trim().toLowerCase();
    const wanted = (tag || '').trim().toLowerCase();
    let shown = 0;

    cards.forEach(function (card) {
      const visible = matches(card, needle, wanted);
      card.hidden = !visible;
      if (visible) {
        shown += 1;
      }
    });

    if (count) {
      count.textContent = shown + ' of ' + cards.length;
    }
    if (empty) {
      empty.hidden = shown !== 0;
    }
    listing.hidden = shown === 0;

    tagLinks.forEach(function (link) {
      const isActive = (link.dataset.tagFilter || '').toLowerCase() === wanted;
      link.classList.toggle('active', isActive);
    });

    if (input && input.value !== (query || '')) {
      input.value = query || '';
    }
  }

  function fromLocation() {
    const params = new URLSearchParams(window.location.search);
    apply(params.get('q') || '', params.get('tag') || '');
  }

  function navigate(query, tag) {
    const params = new URLSearchParams();
    if (query) {
      params.set('q', query);
    }
    if (tag) {
      params.set('tag', tag);
    }
    const search = params.toString();
    window.history.pushState({}, '', search ? '?' + search : window.location.pathname);
    apply(query, tag);
  }

  if (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      const tag = new URLSearchParams(window.location.search).get('tag') || '';
      navigate(input ? input.value : '', tag);
    });
  }

  tagLinks.forEach(function (link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      navigate(input ? input.value : '', link.dataset.tagFilter || '');
    });
  });

  window.addEventListener('popstate', fromLocation);
  fromLocation();
})();
