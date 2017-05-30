jQuery(function($) {
    $.extend({
        dialog: function(message, action, title, buttons, setup) {
            $shade = $('<div style="position:absolute;top:0;right:0;bottom:0;left:0;opacity:0.5;background-color:#000000;z-index:1998" class="trans"></div>');
            $dialog = $('<div style="position:absolute;top:0;right:0;bottom:0;left:0;z-index:1999" class="trans"><form class="round" style="opacity:1;width:600px;margin:50px auto;padding:30px"><div class="padding margin-bottom-huge">' + message + '</div><div class="text-right"></div></form></div>');
            $dialog.find('form div:last').append($.map(buttons, function(button) {
                return $('<a class="button margin">' + button + '</a>');
            }));
            $dialog.find('.button').on('click', function() {
                if($.isFunction(action)) {
                    action($(this).text());
                }
                $dialog.remove();
                $shade.remove();
            });
            $('body').append($shade, $dialog);
        },
        alert: function(message, action, title, buttons, setup) {
            $.dialog(message, action, title, [(buttons && buttons[0]) || "{Sys.Item.ok}"], setup);
        },
        confirm: function(message, action, title, buttons, setup) {
            $.dialog(message, function(button) {
                action(button == (buttons[0] || "{Sys.Item.yes}"));
            }, title, [(buttons && buttons[0]) || "{Sys.Item.yes}", (buttons && buttons[1]) || "{Sys.Item.no}"], setup);
        },
        fatal: function(response, error) {
            $.alert("{Sys.Alert.error} : " + response.status, function() {
                location.herf = "${P}";
            });
        },
        /*set : function(selector, value) {
            if (!selector) {
                return;
            }
            var element = (selector instanceof jQuery) ? selector : $(selector);
            element[element.is(':input') ? 'val' : 'text'](value);
        },*/
    });
    $.fn.extend({
        clearError: function() {
            $(this).find('.input-error').removeClass('input-error').attr('title', null);
        },
        prepare : function() {
            return this.each(function() {
                var $i = $(this);
                //Clear input error
                $i.clearError();
                //Set name attribute if undefined
                $i.find(':input[id]:not([name])').attr('name', function() {
                    return $(this).attr('id');
                });
                //Set datetime from era, year, month, day, hour, minute, second
                var set = function($this, suffixes) {
                    var id = '#' + $this.attr('name');
                    var year = $(id + '_year_').val();
                    if ($.type(year) === 'string' && year.length > 0) {
                        year = (isNaN(year) ? year : Number(year) + Number($(id + '_era_').val()));
                    } else {
                        var empty = true;
                        $.each(suffixes, function(i, suffix) {
                            var v = $(id + suffix).val();
                            if ($.type(v) === 'string' && v.length > 0) {
                                empty = false;
                                return false;
                            }
                        });
                        if (empty) {
                            return $this.val('');
                        }
                    }
                    year = ('0000' + year).substr(-4);
                    $this.val(year + $.map(suffixes, function(suffix) {
                        return ('00' + $(id + suffix).val()).substr(-2);
                    }).join(''));
                };
                $i.find('input.year,input.y').each(function() {
                    set($(this), []);
                });
                $i.find('input.ym').each(function() {
                    set($(this), [ '_month_' ]);
                });
                $i.find('input.date,input.ymd').each(function() {
                    set($(this), [ '_month_', '_day_' ]);
                });
                $i.find('input.ymdh').each(function() {
                    set($(this), [ '_month_', '_day_', '_hour_' ]);
                });
                $i.find('input.datetime,input.ymdhi').each(function() {
                    set($(this), [ '_month_', '_day_', '_hour_', '_minute_' ]);
                });
                $i.find('input.timestamp,input.ymdhis').each(function() {
                    set($(this), [ '_month_', '_day_', '_hour_', '_minute_', '_second_' ]);
                });
            });
        }
    });
    // after loaded
    $('nav').after('<div id="nav-spacer"></div>');
    $('#nav-spacer').height($('nav').height());
    $('#command').after('<div id="command-spacer"></div>');
    $('#command-spacer').height($('#command').height());
    $('[data-load]').each(function() {
        var $this = $(this);
        $this.load($this.attr('data-load'));
    });
    $('.focus:first').focus();
    $(document).on('click', 'a[data-confirm]', function() {
        $this = $(this);
        $.confirm($this.attr('data-confirm'), function(yes) {
            if(yes) {
                location.href = $this.attr('href');
            }
        }, $this.attr('data-title'), [$this.attr('data-yes'), $this.attr('data-no')]);
        return false;
    });
    $(document).on('submit', 'form', function() {
        var $form = $(this);
        if($form.attr('data-submitting-')) {
            return true;
        }
        $form.attr('data-submitting-', 1);
        var isAjax = !!$form.attr('data-ajax');
        var post = function() {
            return $.ajax({
                url : $form.attr('action'),
                type : $form.attr('method') || 'post',
                data : $form.prepare().serialize(),
                dataType : 'json'
            }).then(function(r) {
                var message = r[0];
                if(message) {
                    $.alert(message);
                }
                if($.type(r[1]) === 'string') {
                    location.href = r[1];
                } else {
                    //set item errros
                    $.each(r[1], function(k, v) {
                        $('label[for="' + k + '"],#' + k).addClass('input-error').attr('title', v || message);
                    });
                }
            }, $.fatal);
        };
        var message = $form.attr('data-confirm');
        if(!message) {
            $form.removeAttr('data-submitting-');
            if(!isAjax) {
                return true;
            }
            post();
        } else {
            $.confirm(message, function(yes) {
                if(yes) {
                    if(isAjax) {
                        post();
                    } else {
                        $form.trigger('submit');
                    }
                }
                $form.removeAttr('data-submitting-');
            }, $form.attr('data-title'), [$form.attr('data-yes'), $form.attr('data-no')]);
        }
        return false;
    }).on('reset', function() {
        $(this).clearError();
        $('.focus:first').focus();
    });
});