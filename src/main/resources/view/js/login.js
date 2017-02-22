jQuery(function($) {
    var height = -1;
    var update = function() {
        if($(window).height() != height) {
            height = $(window).height();
            $('.middle').css({ height: height - 136 + 'px', width: $(window).width() + 'px' });
        }
        setTimeout(update, 100)
    };
    update();
    $('.middle').css('display', 'table-cell');
    $('#login_id').focus();
});
