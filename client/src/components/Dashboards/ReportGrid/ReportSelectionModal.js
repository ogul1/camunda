import React from 'react';
import {load} from '../../EntityList/service';

import './ReportSelectionModal.css'

export default class ReportSelectionModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      data: [],
      redirectToEntity: false,
      loaded: false
    };

    this.loadEntities();
  }

  loadEntities = async () => {
    const response = await load('report', undefined, 'lastModified');
    this.setState({
      data: response,
      loaded: true
    });
  }

  closeModal = () => {
    this.props.onCloseModal();
  }

  changeReport = (event) => {
    const report = this.state.data.find((report) => {
      return report.id === event.target.value;
    });
    this.props.onSelectReport(report);
  }

  render () {
    return (
      <div id="report-selection-modal" className='report-selection-modal--modal'>

        <div className='report-selection-modal--modal-content'>
          <span className='report-selection-modal--close' onClick={this.closeModal}>&times;</span>
          <p>Select a report from the list</p>
          <select className='Select' value={this.state.selectedReport} onChange={this.changeReport.bind(this)}>
            <option value=''>Please select report</option>
            {this.state.data.map(report => <option value={report.id} key={report.id}>{report.name}</option>)}
          </select>
        </div>

      </div>
    )
  }
}
