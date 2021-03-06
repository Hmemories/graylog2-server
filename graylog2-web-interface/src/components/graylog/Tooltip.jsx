import React from 'react';
import PropTypes from 'prop-types';
// eslint-disable-next-line no-restricted-imports
import { Tooltip as BootstrapTooltip } from 'react-bootstrap';
import styled, { css } from 'styled-components';

import GraylogThemeProvider from 'theme/GraylogThemeProvider';

const StyledTooltip = styled(BootstrapTooltip)(({ theme }) => css`
  &.top .tooltip-arrow {
    bottom: 0;
  }

  &.top .tooltip-arrow,
  &.top-left .tooltip-arrow,
  &.top-right .tooltip-arrow {
    border-top-color: ${theme.colors.gray[10]};
  }

  &.right .tooltip-arrow {
    border-right-color: ${theme.colors.gray[10]};
  }

  &.left .tooltip-arrow {
    border-left-color: ${theme.colors.gray[10]};
  }

  &.bottom .tooltip-arrow,
  &.bottom-left .tooltip-arrow,
  &.bottom-right .tooltip-arrow {
    border-bottom-color: ${theme.colors.gray[10]};
  }

  .tooltip-inner {
    color: ${theme.utils.readableColor(theme.colors.gray[10])};
    background-color: ${theme.colors.gray[10]};
    max-width: 300px;

    .datapoint-info {
      text-align: left;

      .date {
        color: ${theme.colors.gray[90]};
      }
    }
  }
`);

const Tooltip = ({ children, className, id, placement, positionTop, positionLeft, arrowOffsetTop, arrowOffsetLeft }) => {
  return (
    <GraylogThemeProvider>
      <StyledTooltip className={className}
                     id={id}
                     placement={placement}
                     positionTop={positionTop}
                     positionLeft={positionLeft}
                     arrowOffsetTop={arrowOffsetTop}
                     arrowOffsetLeft={arrowOffsetLeft}>
        {children}
      </StyledTooltip>
    </GraylogThemeProvider>
  );
};

Tooltip.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node.isRequired,
  /**
   * An html id attribute, necessary for accessibility
   * @type {string|number}
   * @required
   */
  id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,

  /**
   * Sets the direction the Tooltip is positioned towards.
   */
  placement: PropTypes.oneOf(['top', 'right', 'bottom', 'left']),

  /**
   * The "top" position value for the Tooltip.
   */
  positionTop: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  /**
   * The "left" position value for the Tooltip.
   */
  positionLeft: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),

  /**
   * The "top" position value for the Tooltip arrow.
   */
  arrowOffsetTop: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  /**
   * The "left" position value for the Tooltip arrow.
   */
  arrowOffsetLeft: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
};

Tooltip.defaultProps = {
  className: undefined,
  placement: 'right',
  positionTop: undefined,
  positionLeft: undefined,
  arrowOffsetTop: undefined,
  arrowOffsetLeft: undefined,
};

/** @component */
export default Tooltip;
