/*
 * Homepage scroll story. Only runs when GSAP + ScrollTrigger actually loaded, the visitor hasn't
 * asked for reduced motion, and the viewport is wide enough that pinning makes sense - otherwise
 * the page is left exactly as it renders from home.css's default (unpinned, fully visible, plain
 * scrolling) state. Nothing here ever hides content that this script then fails to reveal.
 */
(function () {
  function initAetherHome() {
    var root = document.querySelector(".aether-home");
    if (!root) return;

    var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var isNarrow = window.matchMedia("(max-width: 768px)").matches;
    var gsapReady = typeof window.gsap !== "undefined" && typeof window.ScrollTrigger !== "undefined";

    if (reduceMotion || isNarrow || !gsapReady) {
      return;
    }

    // Re-running on an instant-navigation page swap (see the document$ subscription below) must
    // not stack a second set of triggers on top of the first.
    ScrollTrigger.getAll().forEach(function (st) {
      st.kill();
    });

    root.classList.add("js-enhanced");
    gsap.registerPlugin(ScrollTrigger);

    // --- Compose UI / ambient background: the flagship feature - pinned, two stages crossfade
    //     while the glow behind the screenshot builds through the same timeline ---
    var ambientPanel = root.querySelector("#panel-ambient");
    if (ambientPanel) {
      var ambientStages = ambientPanel.querySelectorAll(".ah-stage");
      var glow = ambientPanel.querySelector(".ah-glow");
      var ambientShot = ambientPanel.querySelector(".ah-shot");

      if (ambientStages.length === 2) {
        var ambientTl = gsap.timeline({
          scrollTrigger: {
            trigger: ambientPanel,
            start: "top top",
            end: "+=180%",
            pin: true,
            scrub: 0.5,
          },
        });

        ambientTl
          .to(ambientStages[0], { autoAlpha: 0, y: -30, duration: 1 })
          .fromTo(ambientStages[1], { autoAlpha: 0, y: 30 }, { autoAlpha: 1, y: 0, duration: 1 }, "<");

        if (glow) {
          ambientTl.fromTo(glow, { opacity: 0.15, scale: 0.85 }, { opacity: 0.55, scale: 1.15, duration: 2 }, 0);
        }
        if (ambientShot) {
          ambientTl.fromTo(ambientShot, { scale: 0.96 }, { scale: 1.04, duration: 2 }, 0);
        }
      }
    }

    // --- Transfer settings: headline -> the code typing itself -> what it means, in place ---
    var settingsPanel = root.querySelector("#panel-settings");
    if (settingsPanel) {
      var stages = settingsPanel.querySelectorAll(".ah-stage");
      var codeEl = settingsPanel.querySelector(".ah-code");

      if (codeEl && !codeEl.dataset.split) {
        var chars = codeEl.textContent.split("");
        codeEl.textContent = "";
        chars.forEach(function (ch) {
          var span = document.createElement("span");
          span.textContent = ch;
          span.style.opacity = 0;
          codeEl.appendChild(span);
        });
        codeEl.dataset.split = "true";
      }

      if (stages.length === 3) {
        var settingsTl = gsap.timeline({
          scrollTrigger: {
            trigger: settingsPanel,
            start: "top top",
            end: "+=250%",
            pin: true,
            scrub: 0.5,
          },
        });

        settingsTl
          .to(stages[0], { autoAlpha: 0, y: -30, duration: 1 })
          .fromTo(stages[1], { autoAlpha: 0, y: 30 }, { autoAlpha: 1, y: 0, duration: 0.6 }, "<")
          .to(codeEl.querySelectorAll("span"), { opacity: 1, stagger: 0.05, duration: 0.6 }, ">-0.3")
          .to(stages[1], { autoAlpha: 0, y: -30, duration: 1 }, "+=0.6")
          .fromTo(stages[2], { autoAlpha: 0, y: 30 }, { autoAlpha: 1, y: 0, duration: 1 }, "<");
      }

      var settingsShot = settingsPanel.querySelector(".ah-shot");
      if (settingsShot) {
        gsap.fromTo(
          settingsShot,
          { scale: 0.92, y: 20 },
          {
            scale: 1,
            y: 0,
            scrollTrigger: {
              trigger: settingsPanel,
              start: "top bottom",
              end: "top top",
              scrub: true,
            },
          }
        );
      }
    }

    // --- Shorts: the grid crossfades into the vertical player, sliding up into place ---
    var shortsPanel = root.querySelector("#panel-shorts");
    if (shortsPanel) {
      var grid = shortsPanel.querySelector(".ah-shot--grid");
      var player = shortsPanel.querySelector(".ah-shot--player");

      if (grid && player) {
        gsap
          .timeline({
            scrollTrigger: {
              trigger: shortsPanel,
              start: "top top",
              end: "+=150%",
              pin: true,
              scrub: 0.5,
            },
          })
          .to(grid, { autoAlpha: 0, scale: 0.94, duration: 1 })
          .fromTo(player, { autoAlpha: 0, y: 60 }, { autoAlpha: 1, y: 0, duration: 1 }, "<");
      }
    }

    // Pin distances depend on layout that may not have settled (fonts, images) at first render.
    ScrollTrigger.refresh();
  }

  if (window.document$) {
    // Material's instant-navigation lifecycle observable: fires on first load and again on every
    // client-side page swap, so leaving and returning to the home page re-initializes correctly.
    document$.subscribe(initAetherHome);
  } else {
    document.addEventListener("DOMContentLoaded", initAetherHome);
  }
})();
