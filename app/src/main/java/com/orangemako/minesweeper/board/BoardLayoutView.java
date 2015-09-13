package com.orangemako.minesweeper.board;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;

import com.orangemako.minesweeper.R;
import com.orangemako.minesweeper.exceptions.InitializationException;
import com.orangemako.minesweeper.exceptions.InvalidArgumentException;
import com.orangemako.minesweeper.game.Game;
import com.orangemako.minesweeper.tile.TileView;
import com.orangemako.minesweeper.utilities.GraphicsUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class BoardLayoutView extends ViewGroup implements TileView.TileViewParent {
    static final int DEFAULT_LINE_COLOR = Color.BLACK;

    // In density independent pixels (dp)
    static final int DEFAULT_BORDER_WIDTH = 2;
    static final int DEFAULT_GRIDLINE_WIDTH = 1;

    private Paint mGridLinesPaint;
    private int mGridLineColor;
    private float mGridLineStrokeWidth;

    private Paint mBorderPaint;
    private int mBorderColor;
    private float mBorderStrokeWidth;

    private Board mBoard;
    private Game mGame;

    private TileView[][] mTileViewsGrid;

    public BoardLayoutView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Enable drawing for ViewGroup object
        setWillNotDraw(false);

        extractAttributes(attrs);
        setupDrawObjects();
    }

    private void extractAttributes(AttributeSet attrs) {
        TypedArray attributesArray = getContext().obtainStyledAttributes(attrs, R.styleable.BoardLayoutView);

        // Convert default values to pixels
        float defaultBorderStrokeWidthInPx = GraphicsUtils.dpToPx(DEFAULT_BORDER_WIDTH, getContext());
        float defaultGridLineStrokeWidthInPx = GraphicsUtils.dpToPx(DEFAULT_GRIDLINE_WIDTH, getContext());

        try {
            mGridLineColor = attributesArray.getColor(
                    R.styleable.BoardLayoutView_gridLineColor, DEFAULT_LINE_COLOR);

            mGridLineStrokeWidth = attributesArray.getDimension(
                    R.styleable.BoardLayoutView_gridLineWidth, defaultGridLineStrokeWidthInPx);

            mBorderColor = attributesArray.getColor(
                    R.styleable.BoardLayoutView_borderColor, DEFAULT_LINE_COLOR);

            mBorderStrokeWidth = attributesArray.getDimension(
                    R.styleable.BoardLayoutView_borderWidth, defaultBorderStrokeWidthInPx);
        }
        finally {
           attributesArray.recycle();
        }
    }

    private void setupDrawObjects() {
        mGridLinesPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGridLinesPaint.setColor(mGridLineColor);
        mGridLinesPaint.setStyle(Paint.Style.STROKE);
        mGridLinesPaint.setStrokeWidth(mGridLineStrokeWidth);

        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setColor(mBorderColor);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(mBorderStrokeWidth);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();

        int dimension = mBoard.getDimension();
        int interval = Math.min(getMeasuredWidth(), getMeasuredHeight()) / dimension;

        for(int i = 0; i < childCount; i++ ) {
            TileView tileView = (TileView) getChildAt(i);

            int top = (i / dimension) * interval;
            int bottom = top + interval;
            int left = (i % dimension) * interval;
            int right = left + interval;

            tileView.layout(left, top, right, bottom);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // Ensure the board is a square
        int dimension = Math.min(width, height);

        setMeasuredDimension(dimension, dimension);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        drawGridLines(width, height, canvas);
        drawBorder(width, height, canvas);
    }

    private void drawBorder(int width, int height, Canvas canvas) {
        canvas.drawRect(0, 0, width, height, mBorderPaint);
    }

    private void drawGridLines(int width, int height, Canvas canvas) {
        if(mBoard != null) {
            int dimension = mBoard.getDimension();
            int interval = height / dimension;

            float startX = 0;
            float startY;
            float endX = width;
            float endY;

            // Horizontal lines
            for(int i = 1; i < dimension; i++) {
                startY = endY = interval * i;
                canvas.drawLine(startX, startY, endX, endY, mGridLinesPaint);
            }

            startY = 0;
            endY = height;

            // Vertical lines
            for(int i = 1; i < dimension; i++) {
                startX = endX = interval * i;
                canvas.drawLine(startX, startY, endX, endY, mGridLinesPaint);
            }
        }
    }

    public void setupBoard(Game game) throws InitializationException, InvalidArgumentException {
        this.mGame = game;
        this.mBoard = game.getBoard();

        // Generate children
        addBoardSquareTiles();

        invalidate();
    }

    private void addBoardSquareTiles() throws InitializationException, InvalidArgumentException {
        int dimension = mBoard.getDimension();
        mTileViewsGrid = new TileView[dimension][dimension];

        for(int i = 0; i < dimension; i++) {
            for(int j = 0; j < dimension; j++) {
                TileView tileView = new TileView(getContext(), this, mGame, j, i);
                addView(tileView);

                mTileViewsGrid[i][j] = tileView;
            }
        }

        if(getChildCount() != Math.pow(dimension, 2)) {
            Log.e(BoardLayoutView.class.getName(), "Tile count must be equal to dimension ^ 2.");
            throw new InitializationException();
        }
    }

    @Override
    public void uncoverAdjacentBlankTiles(TileView tileView) {
        Set<TileView> visited = new HashSet<>();
        Stack<TileView> queue = new Stack<>();

        visited.add(tileView);
        queue.add(tileView);

        int dimension = mBoard.getDimension();

        while(!queue.empty()) {
            TileView currentTile = queue.pop();

            int x = currentTile.getXCoordinate();
            int y = currentTile.getYCoordinate();

            int startingY = Math.max(0, y - 1);
            int startingX = Math.max(0, x - 1);

            for(int i = startingY; i < dimension && i <= y + 1; i++) {
                for (int j = startingX; j < dimension && j <= x + 1; j++) {
                    TileView adjacentTile = mTileViewsGrid[i][j];
                    boolean added = visited.add(adjacentTile);

                    if(added && !adjacentTile.doesContainMine() && adjacentTile.getAdjacentMineCount() == 0) {
                        adjacentTile.getDrawableContainer().setLevel(TileView.UNCOVERED);
                        queue.add(adjacentTile);
                    }
                }
            }
        }
    }

    public boolean calculateResult() {
        boolean didWin = true;

        for(int i = 0; i < mBoard.getDimension(); i++) {
            for(int j = 0; j < mBoard.getDimension(); j++) {
                TileView tileView = mTileViewsGrid[i][j];

                boolean isCovered = tileView.getDrawableContainer().getLevel() == TileView.COVERED;

                if(isCovered) {
                    if(tileView.doesContainMine()) {
                        didWin = false;
                    }

                    tileView.getDrawableContainer().setLevel(TileView.UNCOVERED);

                    if(!didWin){
                        break;
                    }
                }
            }
        }
        return didWin;
    }
}
