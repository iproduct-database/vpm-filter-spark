	__nest__ (
		__all__,
		'mdr', {
			__all__: {
				__inited__: false,
				__init__: function (__all__) {
					var absolute_import = __init__ (__world__.__future__).absolute_import;
					var MDR = __init__ (__world__.mdr.MDR);
					var Record = __init__ (__world__.mdr).Record;
					var RecordFinder = __init__ (__world__.mdr).RecordFinder;
					var RecordAligner = __init__ (__world__.mdr).RecordAligner;
					__pragma__ ('<use>' +
						'__future__' +
						'mdr' +
						'mdr.MDR' +
					'</use>')
					__pragma__ ('<all>')
						__all__.MDR = MDR;
						__all__.Record = Record;
						__all__.RecordAligner = RecordAligner;
						__all__.RecordFinder = RecordFinder;
						__all__.absolute_import = absolute_import;
					__pragma__ ('</all>')
				}
			}
		}
	);
