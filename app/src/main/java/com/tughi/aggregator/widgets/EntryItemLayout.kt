package com.tughi.aggregator.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.tughi.aggregator.R

/**
 * A specialized layout for entry lists.
 */
class EntryItemLayout(context: Context, attrs: AttributeSet) : ViewGroup(context, attrs) {

    private lateinit var authorView: View

    private lateinit var faviconView: View
    private lateinit var faviconLayoutParams: MarginLayoutParams

    private lateinit var feedView: View

    private lateinit var selectorView: View

    private lateinit var pinView: View
    private lateinit var pinLayoutParams: MarginLayoutParams

    private lateinit var starView: View
    private lateinit var starLayoutParams: MarginLayoutParams

    private lateinit var timeView: View
    private lateinit var timeLayoutParams: MarginLayoutParams

    private lateinit var titleView: View
    private lateinit var titleLayoutParams: MarginLayoutParams

    override fun addView(child: View, index: Int, params: LayoutParams) {
        when (child.id) {
            R.id.author -> authorView = child
            R.id.favicon -> {
                faviconView = child
                faviconLayoutParams = params as MarginLayoutParams
            }
            R.id.feed_title -> feedView = child
            R.id.selector -> selectorView = child
            R.id.pin -> {
                pinView = child
                pinLayoutParams = params as MarginLayoutParams
            }
            R.id.star -> {
                starView = child
                starLayoutParams = params as MarginLayoutParams
            }
            R.id.time -> {
                timeView = child
                timeLayoutParams = params as MarginLayoutParams
            }
            R.id.title -> {
                titleView = child
                titleLayoutParams = params as MarginLayoutParams
            }
        }

        super.addView(child, index, params)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight

        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        var measuredHeight = paddingTop + paddingBottom

        val maxLineWidth = measuredWidth - paddingLeft - paddingRight - titleLayoutParams.marginStart

        // first line

        pinView.measure(MeasureSpec.makeMeasureSpec(pinLayoutParams.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pinLayoutParams.height, MeasureSpec.EXACTLY))
        starView.measure(MeasureSpec.makeMeasureSpec(starLayoutParams.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(starLayoutParams.height, MeasureSpec.EXACTLY))
        timeView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

        var feedMaxWidth = maxLineWidth
        if (pinView.visibility != View.GONE) {
            feedMaxWidth -= pinLayoutParams.marginStart + pinView.measuredWidth + pinLayoutParams.marginEnd
        }
        if (starView.visibility != View.GONE) {
            feedMaxWidth -= starLayoutParams.marginStart + starView.measuredWidth + starLayoutParams.marginEnd
        }
        feedMaxWidth -= timeLayoutParams.marginStart + timeView.measuredWidth + timeLayoutParams.marginEnd
        feedView.measure(MeasureSpec.makeMeasureSpec(feedMaxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        measuredHeight += feedView.measuredHeight

        // second line

        titleView.measure(MeasureSpec.makeMeasureSpec(maxLineWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        measuredHeight += titleView.measuredHeight

        faviconView.measure(MeasureSpec.makeMeasureSpec(faviconLayoutParams.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(faviconLayoutParams.height, MeasureSpec.EXACTLY))

        // third line

        if (authorView.visibility != View.GONE) {
            authorView.measure(MeasureSpec.makeMeasureSpec(maxLineWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            measuredHeight += authorView.measuredHeight
        }

        // selectors

        selectorView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL

        val width = right - left
        val height = bottom - top
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight

        val titleLeft = paddingLeft + titleLayoutParams.marginStart

        // first line

        val firstLineLeft = titleLeft
        var firstLineRight = width - paddingRight
        val firstLineTop = paddingTop
        val firstLineBottom = firstLineTop + feedView.measuredHeight
        val firstLineBaseline = firstLineTop + feedView.baseline

        if (rtl) {
            feedView.layout(width - firstLineLeft - feedView.measuredWidth, firstLineTop, width - firstLineLeft, firstLineBottom)
        } else {
            feedView.layout(firstLineLeft, firstLineTop, firstLineLeft + feedView.measuredWidth, firstLineBottom)
        }

        firstLineRight -= timeLayoutParams.marginEnd

        val timeLeft = firstLineRight - timeView.measuredWidth
        val timeTop = firstLineBaseline - timeView.baseline
        if (rtl) {
            timeView.layout(width - firstLineRight, firstLineTop, width - timeLeft, timeTop + feedView.measuredHeight)
        } else {
            timeView.layout(timeLeft, firstLineTop, firstLineRight, timeTop + feedView.measuredHeight)
        }

        firstLineRight = timeLeft - timeLayoutParams.marginStart

        if (starView.visibility != View.GONE) {
            firstLineRight -= starLayoutParams.marginEnd

            val starLeft = firstLineRight - starView.measuredWidth
            val starTop = firstLineBaseline - starView.baseline
            if (rtl) {
                starView.layout(width - firstLineRight, starTop, width - starLeft, starTop + starView.measuredHeight)
            } else {
                starView.layout(starLeft, starTop, firstLineRight, starTop + starView.measuredHeight)
            }

            firstLineRight = starLeft - starLayoutParams.marginStart
        }

        if (pinView.visibility != View.GONE) {
            firstLineRight -= pinLayoutParams.marginEnd

            val pinLeft = firstLineRight - pinView.measuredWidth
            val pinTop = firstLineBaseline - pinView.baseline
            if (rtl) {
                pinView.layout(width - firstLineRight, pinTop, width - pinLeft, pinTop + pinView.measuredHeight)
            } else {
                pinView.layout(pinLeft, pinTop, firstLineRight, pinTop + pinView.measuredHeight)
            }
        }

        // second line

        val titleBaseline = firstLineBottom + titleView.baseline
        val titleBottom = firstLineBottom + titleView.measuredHeight
        if (rtl) {
            titleView.layout(width - titleLeft - titleView.measuredWidth, firstLineBottom, width - titleLeft, titleBottom)
        } else {
            titleView.layout(titleLeft, firstLineBottom, titleLeft + titleView.measuredWidth, titleBottom)
        }

        val faviconTop = titleBaseline - faviconView.baseline
        if (rtl) {
            faviconView.layout(width - paddingLeft - faviconView.measuredWidth, faviconTop, width - paddingLeft, faviconTop + faviconView.measuredHeight)
        } else {
            faviconView.layout(paddingLeft, faviconTop, paddingLeft + faviconView.measuredWidth, faviconTop + faviconView.measuredHeight)
        }

        // third line

        if (authorView.visibility != View.GONE) {
            if (rtl) {
                authorView.layout(width - titleLeft - authorView.measuredWidth, titleBottom, width - titleLeft, titleBottom + authorView.measuredHeight)
            } else {
                authorView.layout(titleLeft, titleBottom, titleLeft + authorView.measuredWidth, titleBottom + authorView.measuredHeight)
            }
        }

        // selectors

        if (rtl) {
            selectorView.layout(width - selectorView.measuredWidth, 0, width, height)
        } else {
            selectorView.layout(0, 0, selectorView.measuredWidth, height)
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

}
